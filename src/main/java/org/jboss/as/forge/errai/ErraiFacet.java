package org.jboss.as.forge.errai;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Repository;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.forge.maven.MavenCoreFacet;
import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.JavaType;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.dependencies.DependencyInstaller;
import org.jboss.forge.project.dependencies.ScopeType;
import org.jboss.forge.project.facets.BaseFacet;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.project.facets.WebResourceFacet;
import org.jboss.forge.project.packaging.PackagingType;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.resources.java.JavaResource;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.RequiresFacet;

/**
 * This is the GWT project facet.
 * 
 * @author sandro sonntag
 */
@Alias("gwtfacet")
@RequiresFacet({ MavenCoreFacet.class, JavaSourceFacet.class,
		DependencyFacet.class, WebResourceFacet.class })
public class ErraiFacet extends BaseFacet {
	
	@Inject
	private Shell shell;

	private static final String UTF_8 = "UTF-8";

	public ErraiFacet() {
		super();
	}

	public static final Dependency JAVAEE6 = DependencyBuilder
			.create("org.jboss.spec:jboss-javaee-6.0")
			.setScopeType(ScopeType.IMPORT)
			.setPackagingType(PackagingType.BASIC);

	private final DependencyInstaller installer;

	@Inject
	public ErraiFacet(final DependencyInstaller installer) {
		this.installer = installer;
	}

	@Override
	public boolean install() {
		for (Dependency requirement : getRequiredDependencies()) {
			if (!getInstaller().isInstalled(project, requirement)) {
				DependencyFacet deps = project.getFacet(DependencyFacet.class);
				if (!deps.hasEffectiveManagedDependency(requirement)
						&& !deps.hasDirectManagedDependency(JAVAEE6)) {
					getInstaller().installManaged(project, JAVAEE6);
				}
				getInstaller()
						.install(project, requirement, ScopeType.PROVIDED);
			}
		}
		return true;
	}

	@Override
	public boolean isInstalled() {
		boolean dependencysInstalled = isDepsInstalled();
		boolean pluginInstalled = isPluginInstalled();
		boolean isInstalled = dependencysInstalled && pluginInstalled;
		return isInstalled;
	}

	private void installDependencies() {
		DependencyFacet facet = project.getFacet(DependencyFacet.class);
		for (Dependency requirement : getRequiredDependencies()) {
			DependencyFacet deps = getProject().getFacet(DependencyFacet.class);

			List<Dependency> slf4jVersions = deps
					.resolveAvailableVersions(requirement.toString() + ":[,]");
			Dependency slf4JVersion = shell.promptChoiceTyped(
					"Install which version of the slf4j API?", slf4jVersions);
			installer.install(project, slf4JVersion);
			
			
			facet.addDirectManagedDependency(requirement);
			facet.addDirectDependency(DependencyBuilder.create(requirement)
					.setVersion(null));
		}
	}

	public DependencyInstaller getInstaller() {
		return installer;
	}

	private void installGwtConfiguration() {
		final MavenCoreFacet mvnFacet = project.getFacet(MavenCoreFacet.class);
		Model pom = mvnFacet.getPOM();

		org.apache.maven.model.Plugin plugin = new org.apache.maven.model.Plugin();

		plugin.setArtifactId("gwt-maven-plugin");
		plugin.setGroupId("org.codehaus.mojo");
		plugin.setVersion("2.4.0");

		String gwtModule = getModuleNameStandalone();
		String gwtMessages = getMessagesQualified();

		Xpp3Dom dom;
		try {
			dom = Xpp3DomBuilder
					.build(new ByteArrayInputStream(
							("<configuration>"
									+ "<i18nMessagesBundles><i18nMessagesBundle></i18nMessagesBundle></i18nMessagesBundles>"
									+ "<runTarget>index.html</runTarget>"
									+ "<hostedWebapp>${webappDirectory}</hostedWebapp>"
									+ "<modules><module></module></modules></configuration>")
									.getBytes()), UTF_8);
		} catch (XmlPullParserException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		plugin.setConfiguration(dom);

		dom.getChild("i18nMessagesBundles").getChild("i18nMessagesBundle")
				.setValue(gwtMessages);

		List<PluginExecution> executions = plugin.getExecutions();
		PluginExecution execution = new PluginExecution();
		execution.addGoal("resources");
		execution.addGoal("i18n");
		execution.addGoal("test");
		execution.addGoal("compile");
		executions.add(execution);

		pom.getBuild().getPlugins().add(plugin);
		pom.getBuild().setOutputDirectory("${webappDirectory}/WEB-INF/classes");
		pom.getProperties().put("webappDirectory", "src/main/webapp");
		mvnFacet.setPOM(pom);

		setGWTModule(gwtModule);
		addRepository("mvp4g",
				"http://mvp4g.googlecode.com/svn/maven2/releases");
		addRepository("gwt-bootstrap",
				"http://gwtbootstrap.github.com/maven/snapshots");
	}

	private Collection<Dependency> getRequiredDependencies() {
		Dependency gwtUser = DependencyBuilder
				.create("com.google.gwt:gwt-user:2.4.0:compile:jar");
		Dependency slf4jGwt = DependencyBuilder
				.create("org.jvnet.hudson.main:hudson-gwt-slf4j:2.1.1:compile:jar");
		Dependency slf4j = DependencyBuilder
				.create("org.slf4j:slf4j-api:1.6.1:compile:jar");
		Dependency jaxRs = DependencyBuilder
				.create("javax.ws.rs:jsr311-api:1.1.1:compile:jar");

		Dependency restyGwt = DependencyBuilder
				.create("org.fusesource.restygwt:restygwt:1.2:compile:jar");

		Dependency mvp4g = DependencyBuilder
				.create("com.googlecode.mvp4g:mvp4g:1.4.0:compile:jar");
		mvp4g.getExcludedDependencies().add(
				DependencyBuilder.create("com.google.gwt:gwt-servlet"));

		Dependency hibernateValidatorSources = DependencyBuilder.create(
				"org.hibernate:hibernate-validator:4.2.0.Final:compile:jar")
				.setClassifier("sources");
		Dependency hibernateValidator = DependencyBuilder
				.create("org.hibernate:hibernate-validator:4.2.0.Final:compile:jar");

		Dependency gwtBootstrap = DependencyBuilder
				.create("com.github.gwtbootstrap:gwt-bootstrap:2.0.3.0-SNAPSHOT:compile:jar");

		return Arrays.asList(gwtUser, slf4j, slf4jGwt, jaxRs, restyGwt,
				hibernateValidatorSources, hibernateValidator, mvp4g,
				gwtBootstrap);
	}

	private boolean isPluginInstalled() {
		final MavenCoreFacet mvnFacet = project.getFacet(MavenCoreFacet.class);
		org.apache.maven.model.Plugin gwtPlugin = new org.apache.maven.model.Plugin();
		gwtPlugin.setArtifactId("gwt-maven-plugin");
		gwtPlugin.setGroupId("org.codehaus.mojo");
		boolean pluginInstalled = mvnFacet.getPOM().getBuild().getPlugins()
				.contains(gwtPlugin);
		return pluginInstalled;
	}

	private boolean isDepsInstalled() {
		DependencyFacet deps = project.getFacet(DependencyFacet.class);
		boolean dependencysInstalled = true;
		for (Dependency requirement : getRequiredDependencies()) {
			if (!deps.hasEffectiveDependency(requirement)) {
				return dependencysInstalled = false;
			}
		}
		return dependencysInstalled;
	}

}
