/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ant.internal.ui.preferences;


import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;

/**
 * Content provider that maintains a hierarchical view of classpath URLs which are shown in a tree
 * viewer.
 */
public class AntClasspathContentProvider2 implements ITreeContentProvider {
	protected TreeViewer treeViewer;
	private ViewerSorter sorter= null;
	private ClasspathModel model= null;
		
	public void add(Object o, Object parent) {
		if (parent == null) {
			model.addEntry(o);
		} else if (parent instanceof GlobalClasspathEntries) {
			((GlobalClasspathEntries)parent).addEntry(model.createEntry(o, parent));
		}
	}

	public void removeAll() {
		if (treeViewer != null) {
			treeViewer.remove(model.getEntries());
		}
		model.removeAll();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ant.internal.ui.preferences.AntContentProvider#getSorter()
	 */
	protected ViewerSorter getSorter() {
		return null;
	}
	/**
	 * @see ITreeContentProvider#getParent(Object)
	 */
	public Object getParent(Object element) {
		if (element instanceof ClasspathEntry) {
			return ((ClasspathEntry)element).getParent();
		}
		if (element instanceof GlobalClasspathEntries) {
			return model;
		}
		
		return null;
	}

	/**
	 * @see ITreeContentProvider#hasChildren(Object)
	 */
	public boolean hasChildren(Object element) {
		if (element instanceof ClasspathEntry) {
			return false;
		}
		if (element instanceof GlobalClasspathEntries) {
			return ((GlobalClasspathEntries)element).hasEntries();
			
		} 
		
		if (element instanceof ClasspathModel) {
			return ((ClasspathModel) element).hasEntries();
		}
		return false;
	}

	/**
	 * @see IStructuredContentProvider#getElements(Object)
	 */
	public Object[] getElements(Object inputElement) {
		return getChildren(inputElement);
	}

	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
	 */
	public void dispose() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		treeViewer = (TreeViewer) viewer;
		
		if (newInput != null) {
			treeViewer.setSorter(getSorter());
			model= (ClasspathModel)newInput;
		} else {
			if (model != null) {
				model.removeAll();
			}
			model= null;
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof GlobalClasspathEntries) {
			return ((GlobalClasspathEntries)parentElement).getEntries();
		}
		if (parentElement instanceof ClasspathModel) {
			return ((ClasspathModel)parentElement).getEntries();
		}
		if (parentElement == null) {
			return model.getEntries();
		}
		
		return null;
	}
	
	public void remove(Object o) {
		model.remove(o);
		treeViewer.remove(o);
	}

	public void remove(IStructuredSelection selection) {
		Object[] array= selection.toArray();
		model.removeAll(array);
		treeViewer.remove(array);
	}
}
