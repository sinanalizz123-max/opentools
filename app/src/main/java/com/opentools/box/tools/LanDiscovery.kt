package com.opentools.box.tools

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LanDiscovery {
    private const val RESOLVE_TIMEOUT_MS = 5000L

    fun host(
        context: Context,
        svc: String,
        port: Int,
        name: String,
        onRegistered: (String) -> Unit,
        onError: (String) -> Unit
    ): AutoCloseable {
        return try {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = name
                serviceType = svc
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(si: NsdServiceInfo, code: Int) {
                    try { onError("Registration failed ($code)") } catch (_: Exception) { }
                }
                override fun onUnregistrationFailed(si: NsdServiceInfo, code: Int) { }
                override fun onServiceRegistered(si: NsdServiceInfo) {
                    try { onRegistered(si.serviceName) } catch (_: Exception) { }
                }
                override fun onServiceUnregistered(si: NsdServiceInfo) { }
            }
            try {
                nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                try { onError(e.message ?: "Register failed") } catch (_: Exception) { }
                return AutoCloseable { }
            }
            AutoCloseable { try { nsd.unregisterService(listener) } catch (_: Exception) { } }
        } catch (e: Exception) {
            try { onError(e.message ?: "NSD unavailable") } catch (_: Exception) { }
            AutoCloseable { }
        }
    }

    fun browse(
        context: Context,
        svc: String,
        onPeersChanged: (List<NsdServiceInfo>) -> Unit
    ): AutoCloseable {
        return try {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val found = mutableMapOf<String, NsdServiceInfo>()
            val resolver = Executors.newSingleThreadExecutor { task ->
                Thread(task, "lan-discovery-resolve").apply { isDaemon = true }
            }
            fun resolve(si: NsdServiceInfo) {
                val done = CountDownLatch(1)
                val listener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(failed: NsdServiceInfo, code: Int) {
                        done.countDown()
                    }
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        synchronized(found) { found[si.serviceName] = resolved }
                        try { onPeersChanged(snapshot(found)) } catch (_: Exception) { }
                        done.countDown()
                    }
                }
                try { nsd.resolveService(si, listener) } catch (_: Exception) { return }
                try { done.await(RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS) } catch (_: Exception) { }
            }
            val discovery = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) { }
                override fun onDiscoveryStopped(t: String) { }
                override fun onStartDiscoveryFailed(t: String, code: Int) { }
                override fun onStopDiscoveryFailed(t: String, code: Int) { }
                override fun onServiceFound(si: NsdServiceInfo) {
                    try { resolver.execute { resolve(si) } } catch (_: Exception) { }
                }
                override fun onServiceLost(si: NsdServiceInfo) {
                    synchronized(found) { found.remove(si.serviceName) }
                    try { onPeersChanged(snapshot(found)) } catch (_: Exception) { }
                }
            }
            try {
                nsd.discoverServices(svc, NsdManager.PROTOCOL_DNS_SD, discovery)
            } catch (_: Exception) {
                resolver.shutdownNow()
                return AutoCloseable { }
            }
            AutoCloseable {
                try { nsd.stopServiceDiscovery(discovery) } catch (_: Exception) { }
                resolver.shutdownNow()
            }
        } catch (_: Exception) {
            AutoCloseable { }
        }
    }

    private fun snapshot(found: Map<String, NsdServiceInfo>): List<NsdServiceInfo> =
        synchronized(found) { found.values.toList() }
}
