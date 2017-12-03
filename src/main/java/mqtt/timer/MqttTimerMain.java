/*
 *  Copyright 2017 Alexander Veit
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */


package mqtt.timer;


import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.TimerPingSender;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;


public final class MqttTimerMain
{
    private enum Frequency
    {
        secondly(1000L),
        minutely(60000L),
        hourly(3600000L),
        daily(86400000L),
        hundredmsly(100L),
        tenmsly(10L);

        final long freq;

        private Frequency(long p_lFreq)
        {
            freq = p_lFreq;
        }
    }


    private static final class TimerMqttAsyncClient extends MqttAsyncClient implements AutoCloseable
    {
        protected TimerMqttAsyncClient(String                   p_strServerUri,
                                       ScheduledExecutorService p_executor,
                                       Frequency                p_freq)
            throws MqttException
        {
            super(p_strServerUri,
                  "mqtt-timer-" + p_freq.name() + "-" + UUID.randomUUID().toString(),
                  new MemoryPersistence(),
                  new TimerPingSender(),
                  p_executor);
        }
    }


    private static final class Timer implements Runnable
    {
        private final SimpleDateFormat m_fmt;

        private final ScheduledExecutorService m_executor;

        private final TimerMqttAsyncClient m_client;

        private final Frequency m_freq;

        private final String m_strTopic;

        private final boolean m_bSilent;

        private volatile boolean m_bScheduled;


        protected Timer(ScheduledExecutorService p_executor,
                        TimerMqttAsyncClient     p_client,
                        Frequency                p_freq,
                        String                   p_strTopic,
                        boolean                  p_bSilent)
        {
            m_executor = p_executor;
            m_client   = p_client;
            m_freq     = p_freq;
            m_strTopic = p_strTopic;
            m_bSilent  = p_bSilent;

            // text/JSON date
            m_fmt = new SimpleDateFormat("\"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'\"");

            m_fmt.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        }


        @Override
        public void run()
        {
            if (m_bScheduled)
                _publish();

            if (!m_executor.isShutdown())
            {
                m_executor.schedule(this, _nextDelay(), TimeUnit.MILLISECONDS);

                m_bScheduled = true;
            }
        }


        private void _publish()
        {
            final String      l_strTimestamp;
            final MqttMessage l_message;

            l_strTimestamp = m_fmt.format(new Date());

            l_message = new MqttMessage(l_strTimestamp.getBytes(StandardCharsets.UTF_8));

            l_message.setQos(0); // at most once delivery

            try
            {
                m_client.publish(m_strTopic, l_message).waitForCompletion();

                if (!m_bSilent)
                    System.out.println(l_strTimestamp);
            }
            catch (MqttException l_e)
            {
                if (!m_bSilent)
                    System.out.println(l_e.getMessage());
            }
        }


        private long _nextDelay()
        {
            return m_freq.freq - System.currentTimeMillis() % m_freq.freq;
        }
    }


    private volatile boolean m_bShutdown;


    private MqttTimerMain()
    {
    }


    public static void main(String[] p_args)
    {
        String    l_strServerUri;
        String    l_strUser;
        String    l_strPassword;
        String    l_strTopic;
        Frequency l_freq;
        long      l_lStartDelay;
        boolean   l_bSilent;

        l_strServerUri = null;
        l_strUser      = null;
        l_strPassword  = null;
        l_strTopic     = null;
        l_freq         = Frequency.secondly;
        l_lStartDelay  = -1L;
        l_bSilent      = false;

        for (int l_iPos = 0; l_iPos < p_args.length; l_iPos++)
        {
            if ("--uri".equals(p_args[l_iPos]))
            {
                if (l_iPos >= p_args.length - 1)
                    showUsageAndExit(System.err, 1);

                l_strServerUri = p_args[++l_iPos];
            }
            else if ("--user".equals(p_args[l_iPos]))
            {
                if (l_iPos >= p_args.length - 1)
                    showUsageAndExit(System.err, 1);

                l_strUser = p_args[++l_iPos];
            }
            else if ("--pass".equals(p_args[l_iPos]))
            {
                if (l_iPos >= p_args.length - 1)
                    showUsageAndExit(System.err, 1);

                l_strPassword = p_args[++l_iPos];
            }
            else if ("--freq".equals(p_args[l_iPos]))
            {
                if (l_iPos >= p_args.length - 1)
                    showUsageAndExit(System.err, 1);

                l_freq = Frequency.valueOf(p_args[++l_iPos]);
            }
            else if ("--topic".equals(p_args[l_iPos]))
            {
                if (l_iPos >= p_args.length - 1)
                    showUsageAndExit(System.err, 1);

                l_strTopic = p_args[++l_iPos];
            }
            else if ("--start-delay".equals(p_args[l_iPos]))
            {
                if (l_iPos >= p_args.length - 1)
                    showUsageAndExit(System.err, 1);

                l_lStartDelay = Long.parseLong(p_args[++l_iPos]);
            }
            else if ("--silent".equals(p_args[l_iPos]))
            {
                l_bSilent = true;
            }
            else if ("-h".equals(p_args[l_iPos]) || "--help".equals(p_args[l_iPos]))
            {
                showUsageAndExit(System.out, 0);
            }
            else
            {
                showUsageAndExit(System.err, 1);
            }
        }

        if (l_strServerUri == null)
            showUsageAndExit(System.err, 1);

        if (l_strTopic == null)
            l_strTopic = "timer/" + l_freq.name();

        final MqttTimerMain l_main;

        try
        {
            l_main = new MqttTimerMain();

            Runtime.getRuntime().addShutdownHook
                (new Thread(l_main::_shutdown, "ShutdownHook"));

            l_main._run
                (l_strServerUri, l_strUser, l_strPassword, l_freq, l_strTopic, l_lStartDelay, l_bSilent);

            System.exit(0);
        }
        catch (Exception l_e)
        {
            l_e.printStackTrace(System.err);

            System.exit(1);
        }
    }


    public static void showUsageAndExit(PrintStream p_out, int p_iExitCode)
    {
        p_out.println("mqtt.timer.MqttTimerMain --uri <uri> [OPTION]...");
        p_out.println();
        p_out.println("  --uri <uri>              the server URI, e.g. tcp://localhost:1883");
        p_out.println("  --user <user name>       user name for authentication");
        p_out.println("  --pass <password>        password authentication");
        p_out.println("  --freq <frequency>       supported values are secondly (default),");
        p_out.println("                           minutely, hourly, or daily, hundredmsly,");
        p_out.println("                           tenmsly");
        p_out.println("  --topic <topic name>     an optional topic (default is timer/<frequency>)");
        p_out.println("  --start-delay <num>      start delay in milliseconds before the");
        p_out.println("                           connection to the server is established");
        p_out.println("  --silent                 do not write sent timestamps to stdout");
        p_out.println("  --help, -h               show this help");

        System.exit(p_iExitCode);
    }


    private void _run(String    p_strServerUri,
                      String    p_strUser,
                      String    p_strPassword,
                      Frequency p_freq,
                      String    p_strTopic,
                      long      p_lStartDelay,
                      boolean   p_bSilent)
        throws MqttException, InterruptedException
    {
        final ScheduledExecutorService l_executor;
        final MqttConnectOptions       l_options;

        if (p_lStartDelay > 0L)
        {
            // respond to external signals during start delay
            synchronized (this)
            {
                this.wait(p_lStartDelay);

                if (m_bShutdown)
                    return;
            }
        }

        l_executor = Executors.newScheduledThreadPool(4);

        try (TimerMqttAsyncClient l_client = new TimerMqttAsyncClient(p_strServerUri, l_executor, p_freq))
        {
            final Timer l_timer;

            l_options = new MqttConnectOptions();

            l_options.setCleanSession(true);
            l_options.setAutomaticReconnect(true);

            if (p_strUser != null)
            {
                l_options.setUserName(p_strUser);

                if (p_strPassword != null)
                    l_options.setPassword(p_strPassword.toCharArray());
            }

            l_client.connect(l_options).waitForCompletion(5000L);

            l_timer = new Timer(l_executor, l_client, p_freq, p_strTopic, p_bSilent);

            l_timer.run();

            synchronized (this)
            {
                this.wait();
            }

            l_executor.shutdown();

            if (l_executor.awaitTermination(5L, TimeUnit.SECONDS))
            {
                l_executor.shutdownNow();
                l_executor.awaitTermination(5L, TimeUnit.SECONDS);
            }

            l_client.disconnect();
        }
    }


    private void _shutdown()
    {
        m_bShutdown = true;

        synchronized (this)
        {
            notify();
        }
     }
}
