package com.opentools.box.tools

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

object LanDiscovery {
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
            val resolveListener = { name: String ->
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, code: Int) { }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        synchronized(found) { found[name] = si }
                        try { onPeersChanged(snapshot(found)) } catch (_: Exception) { }
                    }
                }
            }
            val discovery = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) { }
                override fun onDiscoveryStopped(t: String) { }
                override fun onStartDiscoveryFailed(t: String, code: Int) { }
                override fun onStopDiscoveryFailed(t: String, code: Int) { }
                override fun onServiceFound(si: NsdServiceInfo) {
                    Thread {
                        try { nsd.resolveService(si, resolveListener(si.serviceName)) } catch (_: Exception) { }
                    }.start()
                }
                override fun onServiceLost(si: NsdServiceInfo) {
                    synchronized(found) { found.remove(si.serviceName) }
                    try { onPeersChanged(snapshot(found)) } catch (_: Exception) { }
                }
            }
            try {
                nsd.discoverServices(svc, NsdManager.PROTOCOL_DNS_SD, discovery)
            } catch (_: Exception) {
                return AutoCloseable { }
            }
            AutoCloseable { try { nsd.stopServiceDiscovery(discovery) } catch (_: Exception) { } }
        } catch (_: Exception) {
            AutoCloseable { }
        }
    }

    private fun snapshot(found: Map<String, NsdServiceInfo>): List<NsdServiceInfo> =
        synchronized(found) { found.values.toList() }
}
