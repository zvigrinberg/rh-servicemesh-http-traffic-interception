package com.redhat.serviceinterceptor;

import io.quarkus.runtime.annotations.StaticInitSafe;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@StaticInitSafe
public class OpenshiftAnnotationsConfigSource implements ConfigSource {
    public final static int annotationsConfigSourceOrdinal = 280;

    private static final Logger logger = Logger.getLogger("OpenshiftAnnotationsConfigSource.java");
    private static final Map<String, String> annotations = new HashMap<>();
    static
    {
//        String interceptorMode = ConfigProvider.getConfig().getValue("general.interceptor.mode",String.class);
//        Default running mode for proxy is ambassador container
        String interceptorMode = System.getenv("INTERCEPTOR_MODE") == null ? "ambassador" : System.getenv("INTERCEPTOR_MODE");
        if(interceptorMode.trim().equalsIgnoreCase("ambassador")) {
            logger.info("Proxy Interceptor is running in ambassador container mode, overriding configuration and environment variables from pod' Annotations (for all config that exists)");
            String annotationsPath = System.getenv("ANNOTATIONS_PATH");
            if (annotationsPath == null)
                annotationsPath = "/tmp/annotations/..data/annotations";

            loadAnnotationToConfig(annotationsPath);
        }
        else
        {
            logger.info("Proxy Interceptor is running in standalone mode, Configuration is overridden from environment variables only.");
        }
    }

    private static void loadAnnotationToConfig(String annotationsPath) {
        Map<String, String> annotations = buildAnnotationsMap(annotationsPath);
        String manifestName = getAnnotationValue(annotations,"manifestName");
        ifNotEmptySetConfigProperty(manifestName,"general.interceptor.manifestName");
        String protectNullValues = getAnnotationValue(annotations,"protectNullValues");
        ifNotEmptySetConfigProperty(protectNullValues,"general.interceptor.protectNullValues");
        String preserveStringLength = getAnnotationValue(annotations,"preserveStringLength");
        ifNotEmptySetConfigProperty(preserveStringLength,"general.interceptor.preserveStringLength");
        String restrictedText = getAnnotationValue(annotations,"restrictedText");
        ifNotEmptySetConfigProperty(restrictedText,"general.interceptor.restrictedText");
        String jobType = getAnnotationValue(annotations,"jobType");
        ifNotEmptySetConfigProperty(jobType,"general.interceptor.jobType");
        String servicePort = getAnnotationValue(annotations,"servicePort");
        ifNotEmptySetConfigProperty(servicePort,"general.interceptor.servicePort");

    }

    private static String getAnnotationValue(Map<String, String> annotations, String annotationName) {
        return annotations.get(annotationName) !=null ? annotations.get(annotationName).replace("\"","") : "" ;
    }

    private static void ifNotEmptySetConfigProperty(String value, String configPropertyName) {
        if (!value.trim().equals(""))
        {
            annotations.put(configPropertyName, value);
        }
    }

    private static Map<String,String> buildAnnotationsMap(String annotationsPath) {
        Map<String,String> annotations = new HashMap<>();
        File file = new File(annotationsPath);
        try {
            Scanner scanner = new Scanner(file);
            while(scanner.hasNext())
            {
                String[] keyValue = scanner.nextLine().split("=");
                annotations.put(keyValue[0],keyValue[1]);

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return annotations;
    }

    @Override
    public Set<String> getPropertyNames() {
        return annotations.keySet();
    }

    @Override
    public int getOrdinal() {
        return annotationsConfigSourceOrdinal;
    }

    @Override
    public String getValue(final String propertyName) {
        return annotations.get(propertyName);
    }

    @Override
    public String getName() {
        return OpenshiftAnnotationsConfigSource.class.getSimpleName();
    }
}
