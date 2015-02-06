/*
 * Copyright (c) 2013. Knowledge Media Institute - The Open University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.open.kmi.iserve.rest.sal.test;

import com.gargoylesoftware.htmlunit.WebClient;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.Before;
import org.junit.BeforeClass;

import static org.junit.Assert.assertTrue;

/**
 * Taken from Shiro samples-web
 */
public abstract class AbstractContainerTest {
    protected static final int port = 9090;
    protected static final String BASEURI = "http://localhost:" + port + "/";
    protected static PauseableServer server;
    protected final WebClient webClient = new WebClient();

    @BeforeClass
    public static void startContainer() throws Exception {
        if (server == null) {
            server = new PauseableServer();
            Connector connector = new SelectChannelConnector();
            connector.setPort(port);
            server.setConnectors(new Connector[]{connector});
            WebAppContext webContext = new WebAppContext("src/main/webapp", "/iserve");
            server.setHandler(webContext);

            server.start();
            assertTrue(server.isStarted());
        }
    }

    @Before
    public void beforeTest() {
        webClient.setThrowExceptionOnFailingStatusCode(true);
    }

    public void pauseServer(boolean paused) {
        if (server != null) server.pause(paused);
    }

    public static class PauseableServer extends Server {
        public synchronized void pause(boolean paused) {
            try {
                if (paused) for (Connector connector : getConnectors())
                    connector.stop();
                else for (Connector connector : getConnectors())
                    connector.start();
            } catch (Exception e) {
            }
        }
    }
}
