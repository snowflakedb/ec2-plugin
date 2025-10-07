/*
* The MIT License
*
* Original work from ssh-slaves-plugin Copyright (c) 2016, Michael Clarke
* Modified work Copyright (c) 2020-, M Ramon Leon, CloudBees, Inc.
* Modified work:
* - Just the since annotation

* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
*/
package hudson.plugins.ec2.ssh.verifiers;

import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Node;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Helper methods to allow loading and saving of host keys for a computer. Verifiers
 * don't have a reference to the Node or Computer that they're running for at the point
 * they're created, so can only load the existing key to run comparisons against at the
 * point the verifier is invoked during the connection attempt.
 * @author Michael Clarke, M Ramon Leon
 * @since TODO
 */
public final class HostKeyHelper {

    private static final Logger LOGGER = Logger.getLogger(HostKeyHelper.class.getName());
    private static final HostKeyHelper INSTANCE = new HostKeyHelper();

    // Thread-safe cache using ConcurrentHashMap with WeakReference values
    // This provides thread safety while maintaining GC-friendly behavior like WeakHashMap
    private final Map<Computer, WeakReference<HostKey>> cache = new ConcurrentHashMap<>();

    private HostKeyHelper() {
        super();
    }

    public static HostKeyHelper getInstance() {
        return INSTANCE;
    }

    /**
     * Retrieve the currently trusted host key for the requested computer, or null if
     * no key is currently trusted.
     * @param host the Computer to retrieve the key for.
     * @return the currently trusted key for the requested host, or null if no key is trusted.
     * @throws IOException if the host key can not be read from storage
     */
    public HostKey getHostKey(Computer host) throws IOException {
        // Fast path: check cache first (thread-safe with ConcurrentHashMap)
        WeakReference<HostKey> weakRef = cache.get(host);
        if (weakRef != null) {
            HostKey cachedKey = weakRef.get();
            if (cachedKey != null) {
                return cachedKey;
            } else {
                // WeakReference was cleared by GC, remove stale entry
                cache.remove(host, weakRef);
            }
        }
        
        // Slow path: load from disk (outside synchronization for performance)
        File hostKeyFile = getSshHostKeyFile(host.getNode());
        HostKey loadedKey = null;
        
        if (hostKeyFile.exists()) {
            XmlFile xmlHostKeyFile = new XmlFile(hostKeyFile);
            loadedKey = (HostKey) xmlHostKeyFile.read();
            LOGGER.log(Level.FINE, "Loaded host key from disk for computer: {0}", host.getName());
        } else {
            LOGGER.log(Level.FINE, "No host key file found for computer: {0}", host.getName());
        }
        
        // Cache the result using WeakReference for GC-friendly behavior
        if (loadedKey != null) {
            cache.put(host, new WeakReference<>(loadedKey));
        }
        
        return loadedKey;
    }

    /**
     * Persists an SSH key to disk for the requested host. This effectively marks
     * the requested key as trusted for all future connections to the host, until
     * any future save attempt replaces this key.
     * @param host the host the key is being saved for
     * @param hostKey the key to be saved as the trusted key for this host
     * @throws IOException on failure saving the key for the host
     */
    public void saveHostKey(Computer host, HostKey hostKey) throws IOException {
        XmlFile xmlHostKeyFile = new XmlFile(getSshHostKeyFile(host.getNode()));
        xmlHostKeyFile.write(hostKey);
        // Cache the new key using WeakReference
        cache.put(host, new WeakReference<>(hostKey));
        LOGGER.log(Level.FINE, "Saved host key to disk and cache for computer: {0}", host.getName());
    }

    /**
     * Clear the cached host key for a computer. Useful when a computer is being removed
     * or when the host key needs to be refreshed.
     * @param host the computer to clear the cached key for
     */
    public void clearHostKey(Computer host) {
        cache.remove(host);
        LOGGER.log(Level.FINE, "Cleared cached host key for computer: {0}", host.getName());
    }

    /**
     * Get the current cache size (including stale WeakReference entries).
     * Note: This may include entries where the WeakReference has been cleared by GC.
     * @return the number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Clean up stale WeakReference entries that have been cleared by GC.
     * This is called automatically during normal operations but can be invoked manually.
     * @return the number of stale entries removed
     */
    public int cleanupStaleEntries() {
        int removed = 0;
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().get() == null) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.log(Level.FINE, "Cleaned up {0} stale cache entries", removed);
        }
        return removed;
    }

    private File getSshHostKeyFile(Node node) throws IOException {
        return new File(getNodeDirectory(node), "ssh-host-key.xml");
    }

    private File getNodeDirectory(Node node) throws IOException {
        if (null == node) {
            throw new IOException("Could not load key for the requested node");
        }
        return new File(getNodesDirectory(), node.getNodeName());
    }

    private File getNodesDirectory() throws IOException {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            throw new IOException("Jenkins instance is not available");
        }
        return new File(jenkins.getRootDir(), "nodes");
    }
}
