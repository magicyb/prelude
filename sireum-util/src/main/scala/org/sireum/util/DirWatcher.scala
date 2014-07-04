/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sireum.util

import java.nio.file._
import java.nio.file.attribute._
import java.nio.file.StandardWatchEventKinds._
import rx.lang.scala._
import java.util.concurrent.TimeUnit

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object DirWatcher {
  /**
   * @author <a href="mailto:robby@k-state.edu">Robby</a>
   */
  sealed abstract class Event {
    def base : ResourceUri
    def uri : ResourceUri
  }
  /**
   * @author <a href="mailto:robby@k-state.edu">Robby</a>
   */
  final case class Created(
    base : ResourceUri, uri : ResourceUri, isDirectory : Boolean) extends Event
  /**
   * @author <a href="mailto:robby@k-state.edu">Robby</a>
   */
  final case class Modified(
    base : ResourceUri, uri : ResourceUri) extends Event
  /**
   * @author <a href="mailto:robby@k-state.edu">Robby</a>
   */
  final case class Deleted(
    base : ResourceUri, uri : ResourceUri) extends Event

  def apply(p : Path, recursive : Boolean = true, timeout : Int = 1) =
    new DirWatcher(p, recursive, timeout)
}

/**
 * Adapted by <a href="mailto:robby@k-state.edu">Robby</a> from
 * Java Tutorials Code Sample – <a href="http://docs.oracle.com/javase/tutorial/displayCode.html?code=http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java">WatchDir.java</a>
 */
final class DirWatcher(base : Path, recursive : Boolean, timeout : Int) {
  @volatile
  private var term = false
  val watcher = FileSystems.getDefault.newWatchService
  val keys = mmapEmpty[WatchKey, Path]
  val baseUri = FileUtil.toUri(base.toFile)
  val baseUriLength = baseUri.length

  {
    if (Files.exists(base) && Files.isDirectory(base))
      if (recursive)
        registerAll(base)
      else
        register(base)
  }

  private def register(d : Path) {
    val key =
      if (Files.isDirectory(d))
        d.register(watcher, ENTRY_CREATE, ENTRY_DELETE)
      else
        d.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    keys(key) = d
  }

  private def registerAll(d : Path) {
    FileUtil.walkFileTree(d, { (b, p) => register(p) }, true)
  }

  private def toEvent(kind : WatchEvent.Kind[Path], p : Path) = {
    val f = p.toFile
    val rawUri = FileUtil.toUri(f)
    val lastAdj = if (rawUri.endsWith("/")) 1 else 0
    val uri = rawUri.substring(baseUriLength, rawUri.length - lastAdj)
    kind match {
      case `ENTRY_CREATE` => DirWatcher.Created(baseUri, uri, f.isDirectory)
      case `ENTRY_DELETE` => DirWatcher.Deleted(baseUri, uri)
      case `ENTRY_MODIFY` => DirWatcher.Modified(baseUri, uri)
    }
  }

  val observe =
    Observable({ sub : Subscriber[DirWatcher.Event] =>
      (new Thread {
        override def run {
          while (!term && !sub.isUnsubscribed) {
            import scala.collection.JavaConversions._
            val key = watcher.poll(timeout, TimeUnit.SECONDS)
            keys.get(key) match {
              case Some(d) =>
                for (event <- key.pollEvents if event.kind != OVERFLOW) {
                  val e = event.asInstanceOf[WatchEvent[Path]]
                  val p = d.resolve(e.context)
                  try {
                    if (recursive && (e.kind == ENTRY_CREATE) &&
                      Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                      FileUtil.walkFileTree(p, { (b, p) =>
                        if (!sub.isUnsubscribed)
                          sub.onNext(toEvent(e.kind, p))
                      }, false)
                      registerAll(p)
                    } else if (!sub.isUnsubscribed)
                      sub.onNext(toEvent(e.kind, p))
                  } catch {
                    case _ : Exception =>
                  }
                }
                if (!key.reset) {
                  keys.remove(key)
                  if (keys.isEmpty) {
                    term = true
                    sub.onCompleted
                  }
                }
              case _ =>
            }
          }
        }
      }).start
    })

  def stop { term = true }
}