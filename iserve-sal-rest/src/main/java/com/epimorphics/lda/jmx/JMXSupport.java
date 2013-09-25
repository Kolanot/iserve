package com.epimorphics.lda.jmx;

import com.hp.hpl.jena.shared.WrappedException;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class JMXSupport {

    public static void register(String name, Object bean) {
        try {
            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
            ObjectName on = new ObjectName(name);
            ms.registerMBean(bean, on);
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

}
