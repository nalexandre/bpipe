/*
 * Copyright (c) 2011 MCRI, authors
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
package bpipe;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Abstract class that represents a shell command to be executed
 * by Bpipe.  How the command is executed is implementation dependent.
 * <p>
 * Note: the stop() command is not part of this interface because
 * it is not possible to reliably stop a local process job using Java.  
 * This is because Java doesn't give access to the process hierarchy so that 'child'
 * processes can be chased down.  Implementations still need to provide a 
 * "stop" command.
 * <p>
 * Implementations need to properly implement the serializable interface as 
 * Bpipe ensures recovery of state by serializing running commands to the file
 * system.  In particular, attributes that are not valid outside of 
 * the running Bpipe process should be declared transient.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
interface CommandExecutor extends Serializable {
    
    void start(Map cfg, String id, String name, String cmd);
    
    String status();
    
    int waitFor(); 
    
    void stop();
    
    /**
     * Return a list of outputs that should be ignored in terms of 
     * being considered as actual output files for the executed job
     */
    List<String> getIgnorableOutputs();
}
