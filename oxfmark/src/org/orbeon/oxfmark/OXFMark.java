/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxfmark;

import com.jgoodies.plaf.plastic.PlasticXPLookAndFeel;
import edu.stanford.ejalbert.BrowserLauncher;
import org.apache.log4j.PropertyConfigurator;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.XMLProcessorRegistry;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ProcessorDefinition;
import org.orbeon.oxf.util.PipelineUtils;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class OXFMark {

    private static final int HEAT_COUNT = 100;
    private static final int TEST_DURATION = 60;
    private static final int THREAD_COUNT = 5;


    private static long counter;
    private ProcessorService processorService;

    public static void main(String[] arguments) {

        final JFrame frame = new JFrame("OXF Mark 0.4");

        try {
            // Create UI
            UIManager.setLookAndFeel(new PlasticXPLookAndFeel());
            final JProgressBar progressBar = new JProgressBar();
            final JLabel step1 = new JLabel("Step 1: Load OXF...");
            final JLabel step2 = new JLabel("Step 2: Heat VM...");
            final JLabel step3 = new JLabel("Step 3: Run benchmark...");
            final JLabel step4 = new JLabel("Step 4: Finish");
            final JLabel flagLabel = new JLabel(getIcon("flag.png"));
            final JLabel scoreMessage = new JLabel("<html><center>The OXF Mark on<br>this computer is:<br>");
            final JLabel scoreLabel = new JLabel();
            final JButton runBenchmark = new JButton("Start"); {
                runBenchmark.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        runBenchmark.setEnabled(false);
                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    // "Load OXF" (run a first request)
                                    scoreLabel.setText("N/A");
                                    step4.setForeground(Color.gray);
                                    step1.setForeground(Color.black);
                                    final OXFMark oxfMark = new OXFMark();
                                    oxfMark.initOXF();
                                    oxfMark.handleRequest(counter++);

                                    // Heat the VM
                                    step1.setForeground(Color.gray);
                                    step2.setForeground(Color.black);
                                    progressBar.setMaximum(HEAT_COUNT);
                                    for (int i = 0; i < HEAT_COUNT; i++) {
                                        oxfMark.handleRequest(counter++);
                                        progressBar.setValue(i + 1);
                                    }

                                    // Start benchmark
                                    step2.setForeground(Color.gray);
                                    step3.setForeground(Color.black);
                                    final int[] iterations = new int[1];
                                    Thread testTread = new Thread(new Runnable() {
                                        public void run() {
                                            iterations[0] = oxfMark.run();
                                        }
                                    });
                                    testTread.start();

                                    // Update progress bar during test
                                    final int maximumValue = TEST_DURATION * 1000;
                                    progressBar.setMaximum(maximumValue);
                                    final long start = System.currentTimeMillis();
                                    while (true) {
                                        Thread.sleep(500);
                                        long elapsed = System.currentTimeMillis() - start;
                                        progressBar.setValue(Math.min((int)(elapsed), maximumValue));
                                        if (elapsed >= maximumValue)
                                            break;
                                    }

                                    testTread.join();
                                    step3.setForeground(Color.gray);
                                    step4.setForeground(Color.black);
                                    scoreLabel.setText(Integer.toString(iterations[0]));
                                    scoreLabel.setVisible(true);
                                    runBenchmark.setEnabled(true);
                                } catch (Exception e) {
                                    handleException(frame, e);
                                }
                            }
                        }).start();
                    }
                });
            }

            // Place components
            frame.getContentPane().setLayout(new BorderLayout());
            JPanel titlePanel = new JPanel(); {
                frame.getContentPane().add(titlePanel, BorderLayout.NORTH);
                titlePanel.setBackground(Color.white);
                titlePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
                GridBagLayout gridBagLayout = new GridBagLayout();
                titlePanel.setLayout(gridBagLayout);
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridheight = 2;
                    JLabel logo = new JLabel(getIcon("time.png"));
                    logo.setBorder(new EmptyBorder(0, 0, 0, 30));
                    gridBagLayout.setConstraints(logo, constraints);
                    titlePanel.add(logo);
                }
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.weightx = 1;
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    JLabel title = new JLabel("OXF Mark");
                    title.setFont(new Font(title.getFont().getFontName(), Font.BOLD, title.getFont().getSize() + 1));
                    titlePanel.add(title);
                    gridBagLayout.setConstraints(title, constraints);
                }
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.gridy = 1;
                    constraints.weightx = 1;
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    JLabel title = new JLabel("The Server-Side Java Benchmark");
                    titlePanel.add(title);
                    gridBagLayout.setConstraints(title, constraints);
                }
            }
            JPanel centerPanel = new JPanel(); {
                frame.getContentPane().add(centerPanel, BorderLayout.CENTER);
                centerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
                GridBagLayout gridBagLayout = new GridBagLayout();
                centerPanel.setLayout(gridBagLayout);
                {
                    JPanel labelsPanel = new JPanel();
                    labelsPanel.setLayout(new GridLayout(7, 1));
                    labelsPanel.add(step1);
                    labelsPanel.add(step2);
                    labelsPanel.add(step3);
                    labelsPanel.add(step4);
                    step1.setForeground(Color.gray);
                    step2.setForeground(Color.gray);
                    step3.setForeground(Color.gray);
                    step4.setForeground(Color.gray);
                    {
                        labelsPanel.add(new JLabel());
                        labelsPanel.add(new JLabel("For more info on OXF:"));
                        final String url = "http://www.orbeon.com/oxf";
                        JLabel moreInfo = new JLabel(url);
                        moreInfo.setForeground(Color.blue);
                        moreInfo.addMouseListener(new MouseListener() {
                            public void mouseClicked(MouseEvent e) {
                                new Thread(new Runnable() {
                                    public void run() {
                                        try {
                                            BrowserLauncher.openURL(url);
                                        } catch (IOException exception) {
                                            handleException(frame, exception);
                                        }
                                    }
                                }).start();
                            }

                            public void mouseEntered(MouseEvent e) {}
                            public void mouseExited(MouseEvent e) {}
                            public void mousePressed(MouseEvent e) {}
                            public void mouseReleased(MouseEvent e) {}
                        });
                        labelsPanel.add(moreInfo);
                    }

                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 0;
                    constraints.gridy = 0;
                    constraints.gridheight = 3;
                    constraints.weighty = 1;
                    constraints.anchor = GridBagConstraints.WEST;
                    gridBagLayout.setConstraints(labelsPanel, constraints);
                    centerPanel.add(labelsPanel);
                }
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.gridy = 0;
                    constraints.anchor = GridBagConstraints.SOUTH;
                    gridBagLayout.setConstraints(flagLabel, constraints);
                    centerPanel.add(flagLabel, constraints);
                }
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.gridy = 1;
                    gridBagLayout.setConstraints(scoreMessage, constraints);
                    centerPanel.add(scoreMessage, constraints);
                }
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.gridy = 2;
                    constraints.anchor = GridBagConstraints.NORTH;
                    gridBagLayout.setConstraints(scoreLabel, constraints);
                    centerPanel.add(scoreLabel, constraints);
                    scoreLabel.setFont(new Font(scoreLabel.getFont().getFontName(), Font.BOLD, scoreLabel.getFont().getSize() + 1));
                    scoreLabel.setText("N/A");
                }
                {
                    step3.setForeground(Color.gray);
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 0;
                    constraints.gridy = 3;
                    constraints.weightx = 5;
                    constraints.weighty = 1;
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    gridBagLayout.setConstraints(progressBar, constraints);
                    centerPanel.add(progressBar);
                }
                {
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 1;
                    constraints.gridy = 3;
                    constraints.weightx = 1;
                    constraints.weighty = 1;
                    gridBagLayout.setConstraints(runBenchmark, constraints);
                    runBenchmark.setIcon(getIcon("go.png"));
                    runBenchmark.setHorizontalTextPosition(SwingConstants.LEFT);
                    callMethod(runBenchmark, "setIconTextGap", new Class[] {Integer.class}, new Object[] {new Integer(10)});
                    centerPanel.add(runBenchmark, constraints);
                }
            }

            // Display frame
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
            frame.setSize(400, 300);
            frame.setLocation(200, 200);
            frame.setIconImage(getIcon("clock.png").getImage());
            frame.setVisible(true);

        } catch (Exception e) {
            handleException(frame, e);
        }
    }

    private static void handleException(JFrame frame, Exception e) {
        StringWriter stringWriter = new StringWriter();
        OXFException.getRootThrowable(e).printStackTrace(new PrintWriter(stringWriter));
        JOptionPane.showMessageDialog(frame, stringWriter.toString(), "Exception", JOptionPane.ERROR_MESSAGE);
    }

    private static Object callMethod(Object object, String name, Class[] parameterTypes, Object[] parameters) {
        try {
            Method method = object.getClass().getMethod(name, parameterTypes);
            return method == null ? null : method.invoke(object, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        }
    }

    public static ImageIcon getIcon(String resource) {
        return new ImageIcon(OXFMark.class.getResource("/" + resource));
    }

    /**
     * Runs the test
     */
    private int run() {
        try {

            final long start = System.currentTimeMillis();
            final long end = start + TEST_DURATION * 1000;
            final int[] iterationsCount = new int[THREAD_COUNT];
            final long[] threadCounter = new long[THREAD_COUNT];
            final Thread[] threads = new Thread[THREAD_COUNT];
            final long startCounter = counter;

            // Create and run threads
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                threads[threadId] = new Thread(new Runnable() {
                    public void run() {
                        threadCounter[threadId] = startCounter + threadId;
                        while(true) {
                            handleRequest(threadCounter[threadId]);
                            if (System.currentTimeMillis() > end)
                                break;
                            threadCounter[threadId] += THREAD_COUNT;
                            iterationsCount[threadId]++;
                        }
                    }
                });
                threads[threadId].start();
            }

            // Wait for all threads to finish
            int totalCount = 0;
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads[i].join();
                totalCount += iterationsCount[i];
                counter = Math.max(counter, threadCounter[i] + 1);
            }

            return totalCount;
        } catch (InterruptedException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Executes one request
     */
    public void handleRequest(long count) {
        Map parameterMap = new HashMap();
        MarkExternalContext externalContext = new MarkExternalContext(parameterMap);
        parameterMap.put("name", new String[] {Long.toString(count)});
        processorService.service(false, externalContext, new PipelineContext());
        String result = externalContext.getResult();
        if (result.indexOf("Please enter your name:") == -1 || result.indexOf(Long.toString(count) + "!") == -1) {
            System.out.println(result);
            throw new RuntimeException("Unexpected result");
        }
        if (false) {
            // We only do this for testing
            System.out.println(externalContext.getResult());
        }
    }

    /**
     * Initialization, only performed once
     */
    private void initOXF() {
        try {
            // Only log warnings and higher
            Properties log4jProperties = new Properties();
            log4jProperties.put("log4j.rootLogger", "WARN");
            PropertyConfigurator.configure(log4jProperties);

            // Initialize the Resource Manager
            Map props = new HashMap();
            props.put("oxf.resources.factory", "org.orbeon.oxf.resources.ClassLoaderResourceManagerFactory");
            ResourceManagerWrapper.init(props);

            // Initialize properties
            OXFProperties.init("oxf:/config/properties.xml");

            // Create an initial pipeline context
            PipelineContext pipelineContext = new PipelineContext();

            // Register inital processors with the default XML Processor Registry
            Processor processorsDefinition = PipelineUtils.createURLGenerator("oxf:/processors.xml");
            Processor registry = new XMLProcessorRegistry();
            PipelineUtils.connect(processorsDefinition, "data", registry, "config");
            pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, new InitialContext());
            registry.reset(pipelineContext);
            registry.start(pipelineContext);

            // Create processor service
            processorService = new ProcessorService();
            ProcessorDefinition processorDefinition = new ProcessorDefinition();
            processorDefinition.setUri("oxf/processor/page-flow");
            processorDefinition.addInput("controller", "oxf:/config/page-flow.xml");
            processorService.init(processorDefinition, null);
        } catch (NamingException e) {
            throw new OXFException(e);
        }
    }
}
