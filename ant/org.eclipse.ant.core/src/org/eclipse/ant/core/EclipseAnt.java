package org.eclipse.ant.core;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Ant", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.taskdefs.Ant;
import java.io.*;
import java.util.*;

/**
 * Call Ant in a sub-project.
 * This is a bulk copy of the original Ant task.  Unfortunately the original
 * created a new Project in which to run Ant.  This prevents people from
 * providing their own kind of Project and having that propogate into
 * the subprojects.
 * <p>
 * <b>Note:</b> This class/interface is part of an interim API that is still under 
 * development and expected to change significantly before reaching stability. 
 * It is being made available at this early stage to solicit feedback from pioneering 
 * adopters on the understanding that any code that uses this API will almost 
 * certainly be broken (repeatedly) as the API evolves.
 * </p>
 */
public class EclipseAnt extends Ant {

	private File dir = null;
	private String antFile = null;
	private String target = null;
	private String output = null;
	private final static String DEFAULT_ANTFILE = "build.xml";
	private Vector properties = new Vector();
	private Project p1;

	/**
	 * Creates and returns a new <code>Property</code> for the receiver's
	 * target project.
	 * 
	 * @return the new property
	 */
	public Property createProperty() {
		if (p1 == null) {
			reinit();
		}

		Property p=(Property)p1.createTask("property");
		p.setUserProperty(true);
		properties.addElement( p );
		return p;
	}
	
	/**
	 * Performs the execution.
	 * 
	 * @exception BuildException thrown if an execution problem occurs
	 */
	public void execute() throws BuildException {
		try {
			if (p1 == null) {
				reinit();
			}
		
			if (dir == null) 
				dir = project.getBaseDir();

			initializeProject();

			p1.setBaseDir(dir);
			p1.setUserProperty("basedir" , dir.getAbsolutePath());
			
			// Override with local-defined properties
			Enumeration e = properties.elements();
			while (e.hasMoreElements()) {
				Property p=(Property) e.nextElement();
				p.execute();
			}
			
			if (antFile == null) 
				antFile = DEFAULT_ANTFILE;

			File file = new File(antFile);
			if (!file.isAbsolute()) {
				antFile = (new File(dir, antFile)).getAbsolutePath();
				file = (new File(antFile)) ;
				if(!file.isFile() ) {
				  throw new BuildException(Policy.bind("exception.buildFileNotFound",file.toString()));
				}
			}

			p1.setUserProperty("ant.file", antFile );
			ProjectHelper.configureProject(p1, new File(antFile));
			
			if (target == null) {
				target = p1.getDefaultTarget();
			}

			// Are we trying to call the target in which we are defined?
			if (p1.getBaseDir().equals(project.getBaseDir()) &&
				p1.getProperty("ant.file").equals(project.getProperty("ant.file")) &&
				target.equals(this.getOwningTarget().getName())) { 

				throw new BuildException(Policy.bind("exception.antTaskCallingParentTarget"));
			}

			p1.executeTarget(target);
		} finally {
			// help the gc
			p1 = null;
		}
	}
	
	/**
	 * Initializes the receiver.
	 */
	public void init() {
		p1 = new Project();
		p1.setJavaVersionProperty();
		p1.addTaskDefinition("property", 
							 (Class)project.getTaskDefinitions().get("property"));
	}
	
	/**
	 * Initializes the target project.
	 */
	private void initializeProject() {
		Vector listeners = project.getBuildListeners();
		for (int i = 0; i < listeners.size(); i++) {
			p1.addBuildListener((BuildListener)listeners.elementAt(i));
		}

		if (output != null) {
			try {
				PrintStream out = new PrintStream(new FileOutputStream(output));
				DefaultLogger logger = new DefaultLogger();
				logger.setMessageOutputLevel(Project.MSG_INFO);
				logger.setOutputPrintStream(out);
				logger.setErrorPrintStream(out);
				p1.addBuildListener(logger);
			}
			catch(IOException ex) {
				log(Policy.bind("exception.cannotSetOutput",output));
			}
		}

		Hashtable taskdefs = project.getTaskDefinitions();
		Enumeration et = taskdefs.keys();
		while (et.hasMoreElements()) {
			String taskName = (String) et.nextElement();
			Class taskClass = (Class) taskdefs.get(taskName);
			p1.addTaskDefinition(taskName, taskClass);
		}

		Hashtable typedefs = project.getDataTypeDefinitions();
		Enumeration e = typedefs.keys();
		while (e.hasMoreElements()) {
			String typeName = (String) e.nextElement();
			Class typeClass = (Class) typedefs.get(typeName);
			p1.addDataTypeDefinition(typeName, typeClass);
		}

		// set user-define properties
		Hashtable prop1 = project.getProperties();
		e = prop1.keys();
		while (e.hasMoreElements()) {
			String arg = (String) e.nextElement();
			String value = (String) prop1.get(arg);
			p1.setProperty(arg, value);
		}
	}
	
	/**
	 * Reinitializes the receiver.
	 */
	private void reinit() {
		init();
		for (int i=0; i<properties.size(); i++) {
			Property p = (Property) properties.elementAt(i);
			Property newP = (Property) p1.createTask("property");
			newP.setName(p.getName());
			if (p.getValue() != null) {
				newP.setValue(p.getValue());
			}
			if (p.getFile() != null) {
				newP.setFile(p.getFile());
			} 
			if (p.getResource() != null) {
				newP.setResource(p.getResource());
			}
			properties.setElementAt(newP, i);
		}
	}
	
	/**
	 * Sets the receiver's target <b>Ant</b> file.
	 * 
	 * @param s the <b>Ant</b> file location
	 */
	public void setAntfile(String s) {
		this.antFile = s;
	}
	
	/**
	 * Sets the receiver's target directory.
	 * 
	 * @param s the target directory
	 */
	public void setDir(File d) {
		this.dir = d;
	}

	/**
	 * Sets the receiver's output destination.
	 * 
	 * @param s the output destination
	 */
	public void setOutput(String s) {
		this.output = s;
	}
	
	/**
	 * Sets the receiver's <b>Ant</b> target to invoke.
	 * 
	 * @param s the <b>Ant</b> target to invoke
	 */
	public void setTarget(String s) {
		this.target = s;
	}
}
