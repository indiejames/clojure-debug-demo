# debug-demo

A Clojure library to demonstrate debugging Clojure projects with JDI.

## Usage

In a shell set the `JVM_OPTS` environment variable to tell the JVM to listen for debug
connections. For BASH this would be

    export JVM_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8030
  
You can use a different port than 8030, just make to use the same one when calling
`setup-debugger` below.

Now launch a REPL (we'll refer to it as REPL 1) to run the code to be debugged in this directory.

    lein repl
  
 You should see the following message before the REPL startup output:
 
    Listening for transport dt_socket at address: 8030

Now load the `debug-demo.core` namespace.

    user=> (use 'debug-demo.core)
    nil
  
Call the `foo` function to see the output.

    user=> (foo 4)
    4 Hello, World!
    y =  4
    z =  10
    w =  16
    nil
  
Now start another shell and launch a new REPL. _Do not set JVM_OPTS_.

    lein REPL
  
We'll refer to this as REPL 2. You should not see the line about "Listening for transport."

In REPL 2, load the `debug-demo.debug` namespace.

    user=> (use 'debug-demo.debug)
    nil

Now attach to the VM of REPL 1.

    user=> (def vm (setup-debugger 8030))
    Attached to process  Java HotSpot(TM) 64-Bit Server VM
    Listening for events....
    #'user/vm
  
If you don't see the "Attached to process" message then check to make sure that you 
have used the same port that you saw in the "Listening for transpaort" message from
REPL 1. Also, the "Listening for events..." message is printed on a separate thread, so
this line may get mixed in with the other output.

Now that your attached, try setting a breakpoint by executing the following in REPL 2:

    user=> (set-breakpoint vm "/User/jnorton/Clojure/debug-demo/src/debug_demo/core.clj" 10)
  
This sets breakpoint on line 10 of the `foo` function. You should see a lot of diagnstic 
output like this

    Ref type has src path.....
    Ref type has src path.....
    Ref type has src path.....
    Ref type has src path.....
    Ref type has src path.....
    Ref type has src path.....
    Found location...............
    #object[com.sun.tools.jdi.LocationImpl 0x31bb6dac debug_demo.core$foo:10]
    #object[com.sun.tools.jdi.LocationImpl 0x31bb6dac "debug_demo.core$foo:10"]
  
Now execute the foo function again in REPL 1.

    user=> (foo 4)

You should see REPL 1 freeze and the following oputput in REPL 2:

    user=> Got an event............
    Thread:  nREPL-worker-1
    Breakpoint hit at line  10
  
  
The thread name will probably be slightly different. We can use this name to get the 
current stack frame. Execute this in REPL 2:

    user=> (def frame (get-frame vm "nREPL-worker-1" 0))
    #'user/frame
  
Use whatever thread name was returned in place of "nREPL-worker-1". Now we can print the
local variables for this frame by executing the following in REPL 2:

    user=> (print-locals frame)
  
This should give the following output:

    TYPE:  com.sun.tools.jdi.LongValueImpl
    x  =  4
    nil

At this point you can use the other functions in the `debug-demo.debug` namespace to
to step into/over code and examine local variables or set additional breakpoints.  
When you are finished you can resume normal execution in REPL 2 by executing the following 
in REPL 1:

    user=> (continue vm)

## License

(The MIT License)

Copyright © 2016 James Norton

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

### If you want to be awesome.

Credit me with any work derived from this.
