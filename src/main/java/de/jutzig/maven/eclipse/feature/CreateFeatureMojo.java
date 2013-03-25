/*
 * CreateFeatureMojo.java
 *
 * created at 15.03.2013 by utzig <YOURMAILADDRESS>
 *
 * Copyright (c) SEEBURGER AG, Germany. All Rights Reserved.
 */
package de.jutzig.maven.eclipse.feature;


import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.graph.DefaultDependencyNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * Goal which creates an eclipse feature based on the current pom
 */
@Mojo(name = "create-feature", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class CreateFeatureMojo
    extends AbstractMojo
{


    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     */
    @Parameter(defaultValue="${repositorySystemSession}",readonly=true,required=true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     *
     */
    @Parameter(defaultValue="${project.remoteProjectRepositories}",readonly=true,required=true)
    private List<RemoteRepository> projectRepos;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     */
    @Parameter(defaultValue="${project.remotePluginRepositories}", readonly=true,required=true)
    private List<RemoteRepository> pluginRepos;


    /**
     * the feature id
     *
     */
    @Parameter(defaultValue="${project.groupId}.${project.artifactId}",required=true)
    private String id;

    /**
     * the feature label
     *
     */
    @Parameter(defaultValue="${project.name}",required=true)
    private String featureName;

    /**
     * the feature version
     *
     */
    @Parameter(defaultValue="${project.version}",required=true)
    private String version;

    /**
     * the feature provider
     *
     */
    @Parameter(defaultValue="${project.organization.name}")
    private String provider;

    /**
     * the feature url
     *
     */
    @Parameter(defaultValue="${project.url}")
    private String url;

    /**
     * the feature description
     *
     */
    @Parameter(defaultValue="${project.description}")
    private String description;

    /**
     * the feature description
     *
     */
    @Parameter(defaultValue="${project.licenses.license.url}")
    private String licenseUrl;

    /**
     * the feature description
     *
     */
    @Parameter(defaultValue="${project.licenses.license.name}")
    private String license;

    /**
     * where to create the feature.xml
     *
     */
    @Parameter(defaultValue="${project.build.directory}",required=true)
    private File outputDir;
    /**
     * POM
     */
    @Component
    protected MavenProject project;

    private Document doc;

    private Element feature;


    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        try
        {
            writeHeader();

            List<Dependency> dependencies = project.getDependencies();
            for (Dependency dependency : dependencies)
            {
                org.sonatype.aether.graph.Dependency dep2 = RepositoryUtils.toDependency(dependency,
                                                                                         repoSession.getArtifactTypeRegistry());
                DependencyNode node = new DefaultDependencyNode(dep2);
                ArtifactRequest request = new ArtifactRequest(node);
                ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
                Artifact artifact = result.getArtifact();
                writeArtifact(artifact);
            }

            serializeOutput();
        }
        catch (ParserConfigurationException e)
        {
            throw new MojoExecutionException("failed to generate feature.xml", e);
        }
        catch (ArtifactResolutionException e)
        {
            throw new MojoExecutionException("failed to resolve dependency", e);
        }
        catch (TransformerException e)
        {
            throw new MojoExecutionException("failed to serialize results", e);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("failed to generate feature.xml", e);
        }

    }


    private void writeArtifact(Artifact artifact) throws IOException
    {
        Element pluginElement = doc.createElement("plugin");
        feature.appendChild(pluginElement);
        String bundleId = getBundleId(artifact);
        pluginElement.setAttribute("id", bundleId);

        pluginElement.setAttribute("download-size", Long.toString(artifact.getFile().length()));
        pluginElement.setAttribute("install-size", Long.toString(artifact.getFile().length()));
        pluginElement.setAttribute("unpack", "false");

        String bundleVersion = getBundleVersion(artifact);
        pluginElement.setAttribute("version", bundleVersion);

    }


    private String getBundleId(Artifact artifact) throws IOException
    {
        JarFile jar = new JarFile(artifact.getFile());
        String name = jar.getManifest().getMainAttributes().getValue("Bundle-SymbolicName");
        //get rid of attributes
        name = name.replaceAll(";.*", "");
        jar.close();
        return name;
    }

    private String getBundleVersion(Artifact artifact) throws IOException
    {
        JarFile jar = new JarFile(artifact.getFile());
        String version = jar.getManifest().getMainAttributes().getValue("Bundle-Version");
        jar.close();
        return version;
    }


    private void serializeOutput() throws TransformerException
    {
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(outputDir,"feature.xml"));
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(source, result);

    }


    private void writeHeader()
        throws ParserConfigurationException
    {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        doc = builder.newDocument();
        feature = doc.createElement("feature");
        doc.appendChild(feature);
        feature.setAttribute("id", id);
        feature.setAttribute("label", featureName);
        feature.setAttribute("version", version);
        feature.setAttribute("provider-name", provider);

        if(url!=null || description!=null)
        {
            Element description = doc.createElement("description");
            feature.appendChild(description);
            if(url!=null)
                description.setAttribute("url", url);
            if(description!=null)
                description.setTextContent(this.description);
        }

        if(licenseUrl!= null || license!=null)
        {
            Element licenseElement = doc.createElement("license");
            feature.appendChild(licenseElement);
            if(licenseUrl!=null)
                licenseElement.setAttribute("url", licenseUrl);
            if(license!=null)
            licenseElement.setTextContent(this.license);
        }

    }

}
