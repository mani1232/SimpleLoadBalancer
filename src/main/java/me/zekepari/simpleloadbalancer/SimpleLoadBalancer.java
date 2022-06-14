package me.zekepari.simpleloadbalancer;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import com.velocitypowered.api.event.player.ServerPreConnectEvent.ServerResult;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

@Plugin(
        id = "simpleloadbalancer",
        name = "SimpleLoadBalancer",
        version = "1.0",
        description = "Fork Load balancing plugin for Velocity",
        authors = {"daf_t"}
)
public class SimpleLoadBalancer {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final ArrayList<RegisteredServer> servers = new ArrayList();
    private final ArrayList<RegisteredServer> whitelisted = new ArrayList();
    private final List<String> blacklistedStrings = List.of("server1", "server2", "server3", "server4");
    private static RegisteredServer smallestServer;

    @Inject
    private SimpleLoadBalancer(ProxyServer server, Logger logger) {
        this.proxyServer = server;
        this.logger = logger;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        this.initBlacklist();
        this.pingAllServers();
        this.updateSmallestServer();
    }

    @Subscribe
    private void onServerPreConnection(ServerPreConnectEvent event) {
        if (this.whitelisted.contains(event.getOriginalServer())) {
            event.setResult(ServerResult.allowed(smallestServer));
        }

    }

    @Subscribe
    private void onKickFromServer(KickedFromServerEvent event) {
        if (!event.kickedDuringServerConnect()) {
            event.setResult(RedirectPlayer.create(smallestServer));
        }
    }

    private void updateSmallestServer() {
        this.proxyServer.getScheduler().buildTask(this, () -> {
            Optional<RegisteredServer> minServer = this.servers.stream().min(Comparator.comparingInt((server) -> {
                return server.getPlayersConnected().size();
            }));
            minServer.ifPresent((server) -> {
                smallestServer = server;
            });
        }).repeat(400L, TimeUnit.MILLISECONDS).schedule();
    }

    private void pingAllServers() {
        this.proxyServer.getScheduler().buildTask(this, () -> {
            Iterator var1 = this.whitelisted.iterator();

            while(var1.hasNext()) {
                RegisteredServer server = (RegisteredServer)var1.next();
                server.ping().thenAccept((serverPing) -> {
                    if (!this.servers.contains(server)) {
                        this.servers.add(server);
                        this.logger.info("Added " + server.getServerInfo().getName() + " to balance servers list.");
                    }

                }).exceptionally((result) -> {
                    if (this.servers.contains(server)) {
                        this.servers.remove(server);
                        this.logger.info("Removed " + server.getServerInfo().getName() + " from balance servers list.");
                    }

                    return null;
                });
            }

        }).repeat(2L, TimeUnit.SECONDS).schedule();
    }

    private void initBlacklist() {
        Iterator var1 = this.proxyServer.getAllServers().iterator();

        while(var1.hasNext()) {
            RegisteredServer server = (RegisteredServer)var1.next();
            Iterator var3 = this.blacklistedStrings.iterator();

            while(var3.hasNext()) {
                String s = (String)var3.next();
                if (server.getServerInfo().getName().equals(s)) {
                    this.whitelisted.add(server);
                    this.logger.info("Whitelisted " + server.getServerInfo().getName() + " server added.");
                }
            }
        }

    }
}
