package com.xpdustry.claj.server.util;

import java.net.*;

import arc.files.Fi;


public class JarLoader extends URLClassLoader {
  public JarLoader(Fi jar, ClassLoader parent) throws MalformedURLException {
    super(new URL[] {jar.file().toURI().toURL()}, parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    //check for loaded state
    Class<?> loadedClass = findLoadedClass(name);
    if (loadedClass == null) {
      //try to load own class first
      try { loadedClass = findClass(name); }
      //use parent if not found
      catch (ClassNotFoundException e) {
        if (getParent() != null) return getParent().loadClass(name);
        throw e;
      }
    }

    if (resolve) resolveClass(loadedClass);
    return loadedClass;
  }

  public static ClassLoader load(Fi jar) { return load(jar, null); }
  public static ClassLoader load(Fi jar, ClassLoader parent) {
    try { return new JarLoader(jar, parent); }
    catch (MalformedURLException e) { throw new RuntimeException(e); }
  }
}
