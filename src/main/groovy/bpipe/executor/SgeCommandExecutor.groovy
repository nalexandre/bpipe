/*
 * Copyright (c) 2012 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe.executor

import groovy.text.SimpleTemplateEngine;
import groovy.util.logging.Log
import bpipe.Command;
import bpipe.ForwardHost
import bpipe.Utils
import bpipe.PipelineError
import bpipe.CommandStatus

/**
 * Implements a command executor based on the Sun Grid Engine (SGE) resource manager
 * <p>
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Mixin(ForwardHost)
@Log
class SgeCommandExecutor extends TemplateBasedExecutor implements CommandExecutor {
    
    public static final long serialVersionUID = 520230130470104528L;

    /**
     * Start the execution of the command in the SGE environment.
     * <p>
     * The command have to be wrapper by a script shell that will be specified on the 'qsub' command line.
     * This method does the following
     * - Create a command script wrapper named '.cmd.sh' in the job execution directory
     * - Redirect the command stdout to the file ..
     * - Redirect the command stderr to the file ..
     * - The script wrapper save the command exit code in a file named '.cmd.exit' containing
     *   the job exit code. To monitor for job termination will will wait for that file to exist
     */
    @Override
    void start(Map cfg, Command command, File outputDirectory, Appendable outputLog, Appendable errorLog) {
        this.config = cfg
        this.id = command.id
        this.name = command.name;
        this.cmd = command.command?.trim();
        this.command = command

        this.jobDir = ".bpipe/commandtmp/$id"
        File jobDirFile = new File(this.jobDir)
        if(!jobDirFile.exists()) {
            jobDirFile.mkdirs()
        }
 
        // If an account is specified by the config then use that
        log.info "Using account: $config?.account"

        /*
         * Prepare the 'qsub' cmdline. The following options are used
         * - wd: define the job working directory
         * - terse: output just the job id on the output stream
         * - o: define the file to which redirect the standard output
         * - e: define the file to which redirect the error output
         */
        def startCmd = "qsub -V -notify -terse "
        
        // add other parameters (if any)
        if(config?.queue) {
            startCmd += "-q ${config.queue} "
        }
        
        if( config?.sge_request_options ) {
            startCmd += config.sge_request_options + ' '
        }
        
        // at the end append the command script wrapped file name
        startCmd += "$jobDir/$CMD_SCRIPT_FILENAME"
    
        // This is providing backwards compatibility for the original format
        // of the procs parameter supported in the form "orte 1"
        if(config?.procs && !config.procs.toString().isInteger()) {
            def parts = config.procs.toString().split(' ')
            if(parts.size() == 1)
                throw new Exception("Bad format for SGE procs parameter: " + config.procs + ". Expect either integer or form: '<pe> <integer>'")

            // try not to mess with the original config - it could be used in other places
            config = config.clone()
            config.procs = parts[1].toInteger()
            config.sge_pe = parts[0].trim()
        }
        submitJobTemplate(startCmd, cmd, "executor/sge-command.template.sh", outputLog, errorLog)
    }

    @Override
    String status() {
        String result = statusImpl()
        return this.command.status = result
    }

    /**
     * @return The current status as defined by {@link bpipe.CommandStatus}
     */
    String statusImpl() {

        if( !new File(jobDir, CMD_SCRIPT_FILENAME).exists() ) {
            return CommandStatus.UNKNOWN
        }

        if( !commandId ) {
            return CommandStatus.QUEUEING
        }

        File resultExitFile = new File(jobDir, CMD_EXIT_FILENAME )
        if( !resultExitFile.exists() ) {
            return CommandStatus.RUNNING
        }

        return CommandStatus.COMPLETE
    }

    /**
     * Wait for the sub termination
     * @return The program exit code. Zero when everything is OK or a non-zero on error
     */
    @Override
    int waitFor() {

        int count=0
        File exitFile = new File( jobDir, CMD_EXIT_FILENAME )
        while( !stopped ) {

            if( exitFile.exists() ) {
                def val = exitFile.text?.trim()
                if( val.isInteger() ) {
                    // ok. we get the value as integer
                    command.status = CommandStatus.COMPLETE.name()
                    return new Integer(val)
                }

                /*
                 * This could happen if there are latency in the file system.
                 * Try to wait and re-try .. if nothing change make it fail after a fixed amount of time
                 */
                Thread.sleep(500)
                if( count++ < 10 ) { continue }
                log.warn("Missing exit code value for command: '${id}'. Retuning -1 by default")
                return -1
            }


            Thread.sleep(5000)
        }

        return -1
    }

    /**
     * Kill the job execution
     */
    @Override
    void stop() {

        // mark the job as stopped
        // this will break the {@link #waitFor} method as well
        stopped = true

        String cmd = "qdel $commandId"
        log.info "Executing command to stop command $id: $cmd"

        int exitValue
        StringBuilder err
        StringBuilder out

        err = new StringBuilder()
        out = new StringBuilder()
        Process p = Runtime.runtime.exec(cmd)
        Utils.withStreams(p) {
            p.waitForProcessOutput(out,err)
            exitValue = p.waitFor()
        }

        if( exitValue ) {

            def msg = "SGE failed to stop command $id, returned exit code $exitValue from command line: $cmd"
            log.severe "Failed stop command produced output: \n$out\n$err"
            if(!err.toString().trim().isEmpty()) {
                msg += "\n" + Utils.indent(err.toString())
            }
            throw new PipelineError(msg)
        }

        // Successful stop command
        log.info "Successfully called script to stop command $id"
    }

    @Override
    public String statusMessage() {
        return "SGE Job ID: $commandId command: $cmd"
    }

    @Override
    protected String parseCommandId(String text) {
        return text.trim()
    }
}
