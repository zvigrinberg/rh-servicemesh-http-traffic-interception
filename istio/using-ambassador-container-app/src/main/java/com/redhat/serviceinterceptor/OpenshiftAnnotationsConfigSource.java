package com.redhat.serviceinterceptor;

import io.quarkus.runtime.annotations.StaticInitSafe;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@StaticInitSafe
public class OpenshiftAnnotationsConfigSource implements ConfigSource {
    public final static int annotationsConfigSourceOrdinal = 280;
    private static final Map<String, String> annotations = new HashMap<>();
    static
    {
        String annotationsPath = System.getenv("ANNOTATIONS_PATH");
        if (annotationsPath==null)
            annotationsPath="/tmp/annotations/..data/annotations";

        loadAnnotationToConfig(annotationsPath);

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
