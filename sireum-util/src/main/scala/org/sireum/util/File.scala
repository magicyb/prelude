/*
Copyright (c) 2011-2013 Robby, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/

package org.sireum.util

import java.net.URL
import java.net.URI
import java.io.File
import java.io.FilenameFilter

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object FileUtil {

  def fileUri(claz : Class[_], path : String) =
    toUri(new File(claz.getResource(path).toURI))

  def toUri(path : String) : FileResourceUri = toUri(new File(path))

  def toUri(f : File) : FileResourceUri = f.getAbsoluteFile.toURI.toASCIIString

  def toFilePath(fileUri : FileResourceUri) =
    new File(new URI(fileUri)).getAbsolutePath

  def listFiles(dirUri : FileResourceUri, ext : String,
                recursive : Boolean = false,
                result : MArray[FileResourceUri] = marrayEmpty[FileResourceUri]) //
                : ISeq[FileResourceUri] = {
    val dir = new File(new URI(dirUri))
    if (dir.exists)
      dir.listFiles(new FilenameFilter {
        def accept(dir : File, name : String) = name.endsWith(ext)
      }).foreach { f => if (f.isFile) result += toUri(f) }
    if (recursive)
      dir.listFiles.foreach { f =>
        if (f.isDirectory) listFiles(toUri(f), ext, recursive, result)
      }
    result.toList
  }

  def readFile(fileUri : FileResourceUri) : (String, FileResourceUri) = {
    val uri = new URI(fileUri)
    val file = new File(uri)

    assert(file.exists)

    val size = file.length

    assert(size < Int.MaxValue)

    val buffer = new Array[Byte](size.toInt)
    val stream = uri.toURL.openStream
    stream.read(buffer)
    (new String(buffer), file.getAbsoluteFile.toURI.toASCIIString)
  }
}