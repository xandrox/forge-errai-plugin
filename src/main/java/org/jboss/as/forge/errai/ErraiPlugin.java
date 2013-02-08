package org.jboss.as.forge.errai;

import java.util.Arrays;

import javax.inject.Inject;

import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.shell.ShellMessages;
import org.jboss.forge.shell.ShellPrompt;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.DefaultCommand;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeIn;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.SetupCommand;
import org.jboss.forge.spec.javaee.CDIFacet;
import org.jboss.forge.spec.javaee.FacesAPIFacet;
import org.jboss.forge.spec.javaee.FacesFacet;

/**
 *
 */
@Alias("errai")
public class ErraiPlugin implements Plugin
{
   @Inject
   private ShellPrompt prompt;
   
   
   @SetupCommand
   public void setup(final PipeOut out)
   {
      if (!project.hasFacet(FacesAPIFacet.class))
      {
         request.fire(new InstallFacets(FacesAPIFacet.class));
         if (!project.hasFacet(CDIFacet.class)) {
            if (prompt.promptBoolean("Do you also want to install CDI?", true)) {
               request.fire(new InstallFacets(CDIFacet.class));
            }
         }
      }
      FacesFacet facet = project.getFacet(FacesFacet.class);
      if (facet.getFacesServletMappings().isEmpty())
      {
          if (prompt.promptBoolean("Do you also want to install the Faces servlet and mapping?", false)) {
              facet.setFacesMapping("*.xhtml");
              facet.setFacesMapping("/faces/*");
          }
      }
      
      if (project.hasFacet(FacesFacet.class))
      {
         ShellMessages.success(out, "JavaServer Faces is installed.");
      }
   }

   @DefaultCommand
   public void defaultCommand(@PipeIn
   String in, PipeOut out)
   {
      out.println("Executed default command.");
   }

   @Command
   public void command(@PipeIn
   String in, PipeOut out, @Option
   String... args)
   {
      if (args == null)
         out.println("Executed named command without args.");
      else
         out.println("Executed named command with args: " + Arrays.asList(args));
   }

   @Command
   public void prompt(@PipeIn
   String in, PipeOut out)
   {
      if (prompt.promptBoolean("Do you like writing Forge plugins?"))
         out.println("I am happy.");
      else
         out.println("I am sad.");
   }
}
