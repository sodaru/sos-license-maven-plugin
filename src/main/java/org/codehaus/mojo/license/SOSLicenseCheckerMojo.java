package org.codehaus.mojo.license;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.google.gson.Gson;
import org.apache.commons.collections.CollectionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.mojo.license.api.DependenciesTool;
import org.codehaus.mojo.license.api.ThirdPartyTool;
import org.codehaus.mojo.license.model.LicenseMap;
import org.codehaus.mojo.license.utils.SortedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(
        name = "check-license",
        aggregator = true,
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class SOSLicenseCheckerMojo
    extends AggregatorAddThirdPartyMojo
{
    private static final Logger LOG = LoggerFactory.getLogger(SOSLicenseCheckerMojo.class);

    @Parameter(
            property = "license.acceptPomPackaging",
            defaultValue = "true"
    )
    protected boolean acceptPomPackaging;

    @Parameter(property = "reactorProjects", readonly = true, required = true)
    private List<MavenProject> reactorProjects;


    @Parameter(property = "license.validator.endpoint", required = true)
    private String licenseValidatorEndPoint;

    @Parameter(property = "license.validator.key", required = true)
    private String licenseValidatorKey;

    @Parameter(property = "projectName", required = true)
    private String projectName;

    @Parameter(property = "projectUrl", required = true)
    private String projectUrl;

    @Parameter(property = "buildUrl", required = false)
    private String buildUrl;

    @Parameter(property = "buildUser", required = false)
    private String buildUser;


    @Inject
    public SOSLicenseCheckerMojo(ThirdPartyTool thirdPartyTool, DependenciesTool dependenciesTool) {
        super(thirdPartyTool, dependenciesTool);
    }




    @Override
    protected void init() throws Exception {

        super.init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doAction() throws Exception {
        licenseMap = new LicenseMap();

        Artifact pluginArtifact = project.getPluginArtifactMap().get("com.sodaru:sos-license-maven-plugin");

        String groupId = null;
        String artifactId = null;
        String version = null;
        if (pluginArtifact == null) {
            Plugin plugin =
                    project.getPluginManagement().getPluginsAsMap().get("com.sodaru:sos-license-maven-plugin");
            if (plugin != null) {
                groupId = plugin.getGroupId();
                artifactId = plugin.getArtifactId();
                version = plugin.getVersion();
            }
        } else {
            groupId = pluginArtifact.getGroupId();
            artifactId = pluginArtifact.getArtifactId();
            version = pluginArtifact.getVersion();
        }
        if (groupId == null) {
            try {
                final PluginDescriptor pd =
                        (PluginDescriptor) getPluginContext().get("pluginDescriptor");
                groupId = pd.getGroupId();
                artifactId = pd.getArtifactId();
                version = pd.getVersion();
            } catch (ClassCastException e) {
                LOG.warn("Failed to access PluginDescriptor", e);
            }

            if (groupId == null) {
                throw new IllegalStateException(
                        "Failed to determine the license-maven-plugin artifact." + "Please add it to your parent POM.");
            }
        }

        for (MavenProject reactorProject : reactorProjects) {
            if (getProject().equals(reactorProject) && !acceptPomPackaging) {
                // does not process this pom unless specified
                continue;
            }

            AddThirdPartyMojo mojo = new AddThirdPartyMojo(thirdPartyTool, dependenciesTool);
            mojo.initFromMojo(this, reactorProject);

            LicenseMap childLicenseMap = mojo.licenseMap;

            licenseMap.putAll(childLicenseMap);
        }

        postScanResults();

    }

    @Override
    protected boolean checkPackaging() {
        return true;
    }
    private void postScanResults() throws Exception {
        ScanResult scanResult = new ScanResult();

        scanResult.setProject(projectName);
        scanResult.setUrl(projectUrl);

        ScanResult.Meta meta = new ScanResult.Meta();
        meta.setSubmitJobUrl(buildUrl);
        meta.setSubmitterEmail(buildUser);

        scanResult.setMeta(meta);

        List<ScanResult.Dependency> dependenciesList = new LinkedList<ScanResult.Dependency>();

        Set<Map.Entry<String, SortedSet<MavenProject>>> licenses = licenseMap.entrySet();

        for(Map.Entry<String, SortedSet<MavenProject>> entry : licenses) {
            String licenseName = entry.getKey();
            SortedSet<MavenProject> dependencies = entry.getValue();
            for(MavenProject project1 : dependencies) {
                ScanResult.Dependency dep = new ScanResult.Dependency();
                dep.setName(project1.getName());
                dep.setVersion(project1.getVersion());
                List<ScanResult.Dependency.License> licenseList = new LinkedList<ScanResult.Dependency.License>();
                ScanResult.Dependency.License lic = new ScanResult.Dependency.License();
                lic.setId(licenseName);
                lic.setUri("unknown");
                licenseList.add(lic);
                dep.setLicenses(licenseList);
                dependenciesList.add(dep);
            }
        }
        scanResult.setDependencies(dependenciesList);

        Gson gson = new Gson();
        String jsonData = gson.toJson(scanResult);


        URL url = new URL(licenseValidatorEndPoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + licenseValidatorKey);
        connection.setDoOutput(true);

        try {
            OutputStream os = connection.getOutputStream();
            byte[] input = jsonData.getBytes("utf-8");
            os.write(input, 0, input.length);
        } catch (Exception e) {
            LOG.error("error writing to end point", e);
            throw e;
        }

        int statusCode = connection.getResponseCode();
        if(statusCode != 200) {
            LOG.error("end point returned " + statusCode);
            throw new RuntimeException("end point returned " + statusCode);
        }
    }

}
