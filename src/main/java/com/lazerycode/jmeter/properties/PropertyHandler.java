package com.lazerycode.jmeter.properties;

import com.lazerycode.jmeter.JMeterMojo;
import com.lazerycode.jmeter.UtilityFunctions;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Handler to deal with properties file creation.
 *
 * @author Arne Franken, Mark Collin
 */
//TODO: should PropertyHandler really extend JMeterMojo just for using getLog()?
public class PropertyHandler extends JMeterMojo {

    private static EnumMap<JMeterPropertiesFiles, PropertyContainer> masterPropertiesMap = new EnumMap<JMeterPropertiesFiles, PropertyContainer> (JMeterPropertiesFiles.class);
    private File propertySourceDirectory;
    private File propertyOutputDirectory;
    private boolean replaceDefaultProperties;

    public PropertyHandler(File sourceDirectory, File outputDirectory, Artifact jMeterConfigArtifact, boolean replaceDefaultProperties) throws MojoExecutionException {
        setSourceDirectory(sourceDirectory);
        setOutputDirectory(outputDirectory);
        this.replaceDefaultProperties = replaceDefaultProperties;
        try {
            this.loadDefaultProperties(jMeterConfigArtifact);
            this.loadCustomProperties();
        } catch (Exception ex) {
            getLog().error("Error loading properties: " + ex);
        }
    }

    /**
     * Load in the default properties held in the JMeter artifact
     *
     * @param jMeterConfigArtifact
     * @throws MojoExecutionException
     */
    private void loadDefaultProperties(Artifact jMeterConfigArtifact) throws IOException {
        for (JMeterPropertiesFiles propertyFile : JMeterPropertiesFiles.values()) {
            if (propertyFile.createFileIfItDoesntExist()) {
                JarFile propertyJar = new JarFile(jMeterConfigArtifact.getFile());
                InputStream sourceFile = propertyJar.getInputStream(propertyJar.getEntry("bin/" + propertyFile.getPropertiesFileName()));
                Properties defaultPropertySet = new Properties();
                defaultPropertySet.load(sourceFile);
                sourceFile.close();
                getPropertyContainer(propertyFile).setDefaultPropertyObject(defaultPropertySet);
            }
        }
    }

    /**
     * Load in any custom properties that are available in the propertySourceDirectory
     *
     * @throws IOException
     */
    private void loadCustomProperties() throws IOException {
        for (JMeterPropertiesFiles propertyFile : JMeterPropertiesFiles.values()) {
            File sourceFile = new File(this.propertySourceDirectory.getCanonicalFile() + File.separator + propertyFile.getPropertiesFileName());
            if (sourceFile.exists()) {
                InputStream sourceInputStream = new FileInputStream(sourceFile);
                Properties sourcePropertySet = new Properties();
                sourcePropertySet.load(sourceInputStream);
                sourceInputStream.close();
                getPropertyContainer(propertyFile).setCustomPropertyObject(sourcePropertySet);
            }
        }
    }

    /**
     * Check that the source directory exists, throw an error if it does not
     *
     * @param value
     * @throws MojoExecutionException
     */
    private void setSourceDirectory(File value) throws MojoExecutionException {
        if (value.exists()) {
            this.propertySourceDirectory = value;
        } else {
            throw new MojoExecutionException("Property source directory '" + value.getAbsolutePath() + "' does not exist!");
        }
    }

    /**
     * Create the output directory, throw an error if we can't
     *
     * @param value
     * @throws MojoExecutionException
     */
    private void setOutputDirectory(File value) throws MojoExecutionException {
        if (!value.exists()) {
            if (!value.mkdirs()) {
                throw new MojoExecutionException("Property output directory '" + value.getAbsolutePath() + "' cannot be created!");
            }
        }
        this.propertyOutputDirectory = value;
    }

    public void setJMeterProperties(Map<String, String> value) {
        if (UtilityFunctions.isNotSet(value)) return;
        this.getPropertyContainer(JMeterPropertiesFiles.JMETER_PROPERTIES).setCustomPropertyMap(value);
    }

    public void setJMeterSaveServiceProperties(Map<String, String> value) {
        if (UtilityFunctions.isNotSet(value)) return;
        this.getPropertyContainer(JMeterPropertiesFiles.SAVE_SERVICE_PROPERTIES).setCustomPropertyMap(value);
    }

    public void setJMeterSystemProperties(Map<String, String> value) {
        if (UtilityFunctions.isNotSet(value)) return;
        this.getPropertyContainer(JMeterPropertiesFiles.SYSTEM_PROPERTIES).setCustomPropertyMap(value);
    }

    public void setJMeterUpgradeProperties(Map<String, String> value) {
        if (UtilityFunctions.isNotSet(value)) return;
        this.getPropertyContainer(JMeterPropertiesFiles.UPGRADE_PROPERTIES).setCustomPropertyMap(value);
    }

    public void setJmeterUserProperties(Map<String, String> value) {
        if (UtilityFunctions.isNotSet(value)) return;
        this.getPropertyContainer(JMeterPropertiesFiles.USER_PROPERTIES).setCustomPropertyMap(value);
    }

    public void setJMeterGlobalProperties(Map<String, String> value) {
        if (UtilityFunctions.isNotSet(value)) return;
        this.getPropertyContainer(JMeterPropertiesFiles.GLOBAL_PROPERTIES).setCustomPropertyMap(value);
    }

    private PropertyContainer getPropertyContainer(JMeterPropertiesFiles value){
        return this.masterPropertiesMap.get(value);
    }

    /**
     * Create/Copy the properties files used by JMeter into the JMeter directory tree.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException
     *
     */
    public void configureJMeterPropertiesFiles() throws MojoExecutionException {
        for (JMeterPropertiesFiles propertyFile : JMeterPropertiesFiles.values()) {
            Properties modifiedProperties;
            if (this.replaceDefaultProperties) {
                modifiedProperties = new PropertyFileMerger().mergeProperties(getPropertyContainer(propertyFile).getCustomPropertyMap(), getPropertyContainer(propertyFile).getBasePropertiesObject());
            } else {
                modifiedProperties = new PropertyFileMerger().mergeProperties(getPropertyContainer(propertyFile).getCustomPropertyMap(), getPropertyContainer(propertyFile).getMergedPropertiesObject());
            }
            try {
                //Write out final properties file.
                FileOutputStream writeOutFinalPropertiesFile = new FileOutputStream(new File(this.propertyOutputDirectory.getCanonicalFile() + File.separator + propertyFile.getPropertiesFileName()));
                modifiedProperties.store(writeOutFinalPropertiesFile, null);
                writeOutFinalPropertiesFile.flush();
                writeOutFinalPropertiesFile.close();
            } catch (IOException e) {
                throw new MojoExecutionException("Error creating consolidated properties file " + propertyFile.getPropertiesFileName() + ": " + e);
            }
        }
    }
}
