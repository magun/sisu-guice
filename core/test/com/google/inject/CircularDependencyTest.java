/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import static com.google.inject.Asserts.assertContains;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 * @author sameb@google.com (Sam Berlin)
 */
public class CircularDependencyTest extends TestCase {
  
  @Override
  protected void setUp() throws Exception {
    AImpl.nextId = 0;
    BImpl.nextId = 0;
  }

  public void testCircularlyDependentConstructors()
      throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).to(AImpl.class);
        bind(B.class).to(BImpl.class);
      }
    });
    assertCircularDependencies(injector);
  }
  
  public void testCircularlyDependentConstructorsWithProviderMethods()
      throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {}
      
      @Provides @Singleton A a(B b) { return new AImpl(b); }
      @Provides B b(A a) { return new BImpl(a); }
    });
    assertCircularDependencies(injector);
  }
  
  public void testCircularlyDependentConstructorsWithProviderInstances()
      throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).toProvider(new Provider<A>() {
          @Inject Provider<B> bp;
          public A get() {
            return new AImpl(bp.get());
          }
        }).in(Singleton.class);
        bind(B.class).toProvider(new Provider<B>() {
          @Inject Provider<A> ap;
          public B get() {
            return new BImpl(ap.get());
          }
        });
      }
    });
    assertCircularDependencies(injector);
  }
  
  public void testCircularlyDependentConstructorsWithProviderKeys()
      throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).toProvider(AP.class).in(Singleton.class);
        bind(B.class).toProvider(BP.class);
      }
    });
    assertCircularDependencies(injector);
  }
  
  public void testCircularlyDependentConstructorsWithProvidedBy()
      throws CreationException {
    Injector injector = Guice.createInjector();
    assertCircularDependencies(injector);
  }
  
  private void assertCircularDependencies(Injector injector) {
    A a = injector.getInstance(A.class);
    assertNotNull(a.getB().getA());
    assertEquals(0, a.id());
    assertEquals(a.id(), a.getB().getA().id());
    assertEquals(0, a.getB().id());
    assertEquals(1, AImpl.nextId);
    assertEquals(1, BImpl.nextId);
    assertSame(a, injector.getInstance(A.class));
  }

  @ProvidedBy(AutoAP.class)
  public interface A {
    B getB();
    int id();
  }

  @Singleton
  static class AImpl implements A {
    static int nextId;
    int id = nextId++;
    
    final B b;
    @Inject public AImpl(B b) {
      this.b = b;
    }
    public int id() {
      return id;
    }
    public B getB() {
      return b;
    }
  }
  
  static class AP implements Provider<A> {
    @Inject Provider<B> bp;
    public A get() {
      return new AImpl(bp.get());
    }
  }
  
  @Singleton
  static class AutoAP implements Provider<A> {
    @Inject Provider<B> bp;
    A a;
    
    public A get() {
      if (a == null) {
        a = new AImpl(bp.get());
      }
      return a;
    }
  }

  @ProvidedBy(BP.class)
  public interface B {
    A getA();
    int id();
  }

  static class BImpl implements B {
    static int nextId;
    int id = nextId++;
    
    final A a;
    @Inject public BImpl(A a) {
      this.a = a;
    }
    public int id() {
      return id;
    }
    public A getA() {
      return a;
    }
  }
  
  static class BP implements Provider<B> {
    Provider<A> ap;
    @Inject BP(Provider<A> ap) {
      this.ap = ap;
    }
    public B get() {
      return new BImpl(ap.get());
    }
  }

  public void testUnresolvableCircularDependency() {
    try {
      Guice.createInjector().getInstance(C.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C.class.getName() + " to support a circular dependency, ",
          "but it is not an interface.");
    }
  }
  
  public void testUnresolvableCircularDependenciesWithProviderInstances() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {}        
        @Provides C c(D d) { return null; }
        @Provides D d(C c) { return null; }
      }).getInstance(C.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C.class.getName() + " to support a circular dependency, ",
          "but it is not an interface.");
    }
  }
  
  public void testUnresolvableCircularDependenciesWithProviderKeys() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(C2.class).toProvider(C2P.class);
          bind(D2.class).toProvider(D2P.class);
        }
      }).getInstance(C2.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C2.class.getName() + " to support a circular dependency, ",
          "but it is not an interface.");
    }
  }
  
  public void testUnresolvableCircularDependenciesWithProvidedBy() {
    try {
      Guice.createInjector().getInstance(C2.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C2.class.getName() + " to support a circular dependency, ",
          "but it is not an interface.");
    }
  }

  static class C {
    @Inject C(D d) {}
  }
  static class D {
    @Inject D(C c) {}
  }
  
  static class C2P implements Provider<C2> {
    @Inject Provider<D2> dp;
    public C2 get() {
      dp.get();
      return null;
    }
  }
  static class D2P implements Provider<D2> {
    @Inject Provider<C2> cp;
    public D2 get() {
      cp.get();
      return null;
    }
  }
  @ProvidedBy(C2P.class)
  static class C2 {
    @Inject C2(D2 d) {}
  }
  @ProvidedBy(D2P.class)
  static class D2 {
    @Inject D2(C2 c) {}
  }
  
  public void testDisabledCircularDependency() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().disableCircularProxies();
        }
      }).getInstance(C.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C.class.getName() + " to support a circular dependency, ",
          "but circular proxies are disabled.");
    }
  }
  
  public void testDisabledCircularDependenciesWithProviderInstances() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          binder().disableCircularProxies();
        }        
        @Provides C c(D d) { return null; }
        @Provides D d(C c) { return null; }
      }).getInstance(C.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C.class.getName() + " to support a circular dependency, ",
          "but circular proxies are disabled.");
    }
  }
  
  public void testDisabledCircularDependenciesWithProviderKeys() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          binder().disableCircularProxies();
          bind(C2.class).toProvider(C2P.class);
          bind(D2.class).toProvider(D2P.class);
        }
      }).getInstance(C2.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C2.class.getName() + " to support a circular dependency, ",
          "but circular proxies are disabled.");
    }
  }
  
  public void testDisabledCircularDependenciesWithProvidedBy() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().disableCircularProxies();
        }
      }).getInstance(C2.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + C2.class.getName() + " to support a circular dependency, ",
          "but circular proxies are disabled.");
    }
  }

  /**
   * As reported by issue 349, we give a lousy trace when a class is circularly
   * dependent on itself in multiple ways.
   */
  public void testCircularlyDependentMultipleWays() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        binder.bind(A.class).to(E.class);
        binder.bind(B.class).to(E.class);
      }
    });
    injector.getInstance(A.class);
  }
  
  public void testDisablingCircularProxies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        binder().disableCircularProxies();
        binder.bind(A.class).to(E.class);
        binder.bind(B.class).to(E.class);
      }
    });
    
    try {
      injector.getInstance(A.class);
      fail("expected exception");
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + A.class.getName() + " to support a circular dependency, but circular proxies are disabled", 
          "Tried proxying " + B.class.getName() + " to support a circular dependency, but circular proxies are disabled");
    }
  }

  @Singleton
  static class E implements A, B {
    @Inject
    public E(A a, B b) {}

    public B getB() {
      return this;
    }

    public A getA() {
      return this;
    }
    
    public int id() {
      return 0;
    }
  }


  public void testCircularDependencyProxyDelegateNeverInitialized() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(F.class).to(RealF.class);
        bind(G.class).to(RealG.class);
      }
    });
    F f = injector.getInstance(F.class);
    assertEquals("F", f.g().f().toString());
    assertEquals("G", f.g().f().g().toString());

  }

  public interface F {
    G g();
  }

  @Singleton
  public static class RealF implements F {
    private final G g;
    @Inject RealF(G g) {
      this.g = g;
    }

    public G g() {
      return g;
    }

    @Override public String toString() {
      return "F";
    }
  }

  public interface G {
    F f();
  }

  @Singleton
  public static class RealG implements G {
    private final F f;
    @Inject RealG(F f) {
      this.f = f;
    }

    public F f() {
      return f;
    }

    @Override public String toString() {
      return "G";
    }
  }
  
  /**
   * Tests that ProviderInternalFactory can detect circular dependencies
   * before it gets to Scopes.SINGLETON.  This is especially important
   * because the failure in Scopes.SINGLETON doesn't have enough context to
   * provide a decent error message.
   */
  public void testCircularDependenciesDetectedEarlyWhenDependenciesHaveDifferentTypes() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Number.class).to(Integer.class);
      }
      
      @Provides @Singleton Integer provideInteger(List list) { 
        return new Integer(2);
      }
      
      @Provides List provideList(Integer integer) {
        return new ArrayList();
      }
    });
    try {
      injector.getInstance(Number.class);
      fail();
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(),
          "Tried proxying " + Integer.class.getName() + " to support a circular dependency, ",
          "but it is not an interface.");      
    }
  }
  
  public void testPrivateModulesDontTriggerCircularErrorsInProviders() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new PrivateModule() {
          @Override
          protected void configure() {
            bind(Foo.class);
            expose(Foo.class);
          }
          @Provides String provideString(Bar bar) {
            return new String("private 1, " + bar.string);
          }
        });
        install(new PrivateModule() {
          @Override
          protected void configure() {
            bind(Bar.class);
            expose(Bar.class);
          }
          @Provides String provideString() {
            return new String("private 2");
          }
        });
      }
    });
    Foo foo = injector.getInstance(Foo.class);
    assertEquals("private 1, private 2", foo.string);
  }
  static class Foo {
    @Inject String string;
  }
  static class Bar {
    @Inject String string;
  }
}
