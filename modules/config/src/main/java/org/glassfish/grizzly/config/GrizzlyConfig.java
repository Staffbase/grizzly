/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.config.dom.NetworkConfig;
import org.glassfish.grizzly.config.dom.NetworkListener;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import org.jvnet.hk2.component.Habitat;

/**
 * Created Nov 24, 2008
 *
 * @author <a href="mailto:justin.lee@glassfish.com">Justin Lee</a>
 */
public class GrizzlyConfig {
    private static final Logger logger = Logger.getLogger(GrizzlyConfig.class.getName());
    private final NetworkConfig config;
    private Habitat habitat;
    private final List<GrizzlyServiceListener> listeners = new ArrayList<GrizzlyServiceListener>();

    public GrizzlyConfig(String file) {
        habitat = Utils.getHabitat(file);
        config = habitat.getComponent(NetworkConfig.class);
    }

    public NetworkConfig getConfig() {
        return config;
    }

    public Habitat getHabitat() {
        return habitat;
    }

    public List<GrizzlyServiceListener> getListeners() {
        return listeners;
    }

    public void setupNetwork() throws IOException {
        validateConfig(config);
        synchronized (listeners) {
            for (final NetworkListener listener : config.getNetworkListeners().getNetworkListener()) {
                final GrizzlyServiceListener grizzlyListener = new GrizzlyServiceListener(listener);
                listeners.add(grizzlyListener);
                final Thread thread = new DefaultWorkerThread(Grizzly.DEFAULT_ATTRIBUTE_BUILDER,
                    grizzlyListener.getName(), new ListenerRunnable(grizzlyListener));
                thread.setDaemon(true);
                thread.start();
            }
            try {
                Thread.sleep(1000); // wait for the system to finish setting up the listener
            } catch (InterruptedException e) {
                logger.warning(e.getMessage());
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    public void shutdownNetwork() {
        synchronized (listeners) {
            for (GrizzlyServiceListener listener : listeners) {
                try {
                    listener.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            listeners.clear();
        }
    }

    private static void validateConfig(NetworkConfig config) {
        for (final NetworkListener listener : config.getNetworkListeners().getNetworkListener()) {
            listener.findHttpProtocol();
        }
    }

    public void shutdown() throws IOException {
        synchronized (listeners) {
            for (GrizzlyServiceListener listener : listeners) {
                try {
                    listener.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            listeners.clear();
        }
    }

    public static boolean toBoolean(String value) {
        final String v = value == null ? value : value.trim();
        return v != null && ("true".equals(v) || "yes".equals(v) || "on".equals(v) || "1".equals(v));
    }

    private static class ListenerRunnable implements Runnable {
        private final GrizzlyServiceListener grizzlyListener;

        public ListenerRunnable(GrizzlyServiceListener grizzlyListener) {
            this.grizzlyListener = grizzlyListener;
        }

        public void run() {
            try {
                grizzlyListener.start();
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e.getMessage());
            }
        }
    }
}
