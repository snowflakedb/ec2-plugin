package hudson.plugins.ec2.util;

import hudson.plugins.ec2.EC2Computer;
import hudson.plugins.ec2.ssh.verifiers.HostKey;
import hudson.plugins.ec2.ssh.verifiers.HostKeyHelper;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;

public final class SSHClientHelper {

    private static final Logger LOGGER = Logger.getLogger(SSHClientHelper.class.getName());
    private static final SSHClientHelper INSTANCE = new SSHClientHelper();
    
    // Cache preferred signatures to avoid repeated calls to HostKeyHelper.getHostKey()
    // This reduces call frequency back to pre-February 2025 levels
    private final Map<EC2Computer, WeakReference<List<BuiltinSignatures>>> signatureCache = new ConcurrentHashMap<>();

    private SSHClientHelper() {}

    public static SSHClientHelper getInstance() {
        return INSTANCE;
    }

    /**
     * Set up an SSH client configured for the given {@link EC2Computer}.
     *
     * @param computer the {@link EC2Computer} the created client will connect to
     * @return an SSH client configured for this {@link EC2Computer}
     */
    public SshClient setupSshClient(EC2Computer computer) {
        SshClient client = SshClient.setUpDefaultClient();

        List<BuiltinSignatures> preferred = getPreferredSignatures(computer);
        if (!preferred.isEmpty()) {
            LinkedHashSet<NamedFactory<Signature>> signatureFactoriesSet = new LinkedHashSet<>(preferred);
            signatureFactoriesSet.addAll(client.getSignatureFactories());
            client.setSignatureFactories(new ArrayList<>(signatureFactoriesSet));
        }

        return client;
    }

    /**
     * Return an ordered list of signature algorithms that should be used. Noticeably, if a {@link HostKey} already exists for this
     * {@link EC2Computer}, the {@link HostKey} algorithm will be attempted first.
     *
     * @param computer return a list of signature for this computer.
     * @return an ordered list of signature algorithms that should be used.
     */
    public List<BuiltinSignatures> getPreferredSignatures(EC2Computer computer) {
        // Check cache first to avoid repeated calls to HostKeyHelper.getHostKey()
        WeakReference<List<BuiltinSignatures>> weakRef = signatureCache.get(computer);
        if (weakRef != null) {
            List<BuiltinSignatures> cached = weakRef.get();
            if (cached != null) {
                LOGGER.log(Level.FINE, "Using cached preferred signatures for computer: {0}", computer.getName());
                return cached;
            } else {
                // WeakReference was cleared by GC, remove stale entry
                signatureCache.remove(computer, weakRef);
            }
        }
        
        String trustedAlgorithm;
        try {
            HostKey trustedHostKey = HostKeyHelper.getInstance().getHostKey(computer);
            if (trustedHostKey == null) {
                LOGGER.log(Level.FINE, "No trusted host key found for computer: {0}", computer.getName());
                List<BuiltinSignatures> emptyList = List.of();
                signatureCache.put(computer, new WeakReference<>(emptyList));
                return emptyList;
            }
            trustedAlgorithm = trustedHostKey.getAlgorithm();
            LOGGER.log(Level.FINE, "Found trusted host key algorithm '{0}' for computer: {1}", new Object[]{trustedAlgorithm, computer.getName()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to retrieve host key for computer: " + computer.getName(), e);
            List<BuiltinSignatures> emptyList = List.of();
            signatureCache.put(computer, new WeakReference<>(emptyList));
            return emptyList;
        }

        List<BuiltinSignatures> preferred;
        switch (trustedAlgorithm) {
            case "ssh-rsa":
                preferred = List.of(
                        BuiltinSignatures.rsa,
                        BuiltinSignatures.rsaSHA256,
                        BuiltinSignatures.rsaSHA256_cert,
                        BuiltinSignatures.rsaSHA512,
                        BuiltinSignatures.rsaSHA512_cert);
                break;
            case "ecdsa-sha2-nistp256":
                preferred = List.of(BuiltinSignatures.nistp256, BuiltinSignatures.nistp256_cert);
                break;
            case "ecdsa-sha2-nistp384":
                preferred = List.of(BuiltinSignatures.nistp384, BuiltinSignatures.nistp384_cert);
                break;
            case "ecdsa-sha2-nistp521":
                preferred = List.of(BuiltinSignatures.nistp521, BuiltinSignatures.nistp521_cert);
                break;
            case "ssh-ed25519":
                preferred = List.of(
                        BuiltinSignatures.ed25519, BuiltinSignatures.ed25519_cert, BuiltinSignatures.sk_ssh_ed25519);
                break;
            default:
                LOGGER.log(Level.FINE, "Unknown host key algorithm '{0}' for computer: {1}", new Object[]{trustedAlgorithm, computer.getName()});
                preferred = List.of();
                break;
        }

        // Keep only supported algorithms
        List<BuiltinSignatures> supportedPreferred = NamedFactory.setUpBuiltinFactories(true, preferred);
        
        // Cache the result using WeakReference for GC-friendly behavior
        signatureCache.put(computer, new WeakReference<>(supportedPreferred));
        LOGGER.log(Level.FINE, "Cached {0} preferred signatures for computer: {1}", new Object[]{supportedPreferred.size(), computer.getName()});
        
        return supportedPreferred;
    }
    
    /**
     * Clear the signature cache for a computer. Useful when a computer is being removed
     * or when the host key needs to be refreshed.
     * @param computer the computer to clear the cached signatures for
     */
    public void clearSignatureCache(EC2Computer computer) {
        signatureCache.remove(computer);
        LOGGER.log(Level.FINE, "Cleared signature cache for computer: {0}", computer.getName());
    }

    /**
     * Clear all signature caches. Useful for cleanup or testing.
     * @return the number of cache entries cleared
     */
    public int clearAllSignatureCaches() {
        int size = signatureCache.size();
        signatureCache.clear();
        LOGGER.log(Level.FINE, "Cleared all signature caches ({0} entries)", size);
        return size;
    }

    /**
     * Get the current signature cache size (including stale WeakReference entries).
     * @return the number of cached signature entries
     */
    public int getSignatureCacheSize() {
        return signatureCache.size();
    }
}
