/**
 * Copyright (C) 2011 Google Inc.
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


package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.Dependency;

import javax.inject.Provider;

/**
 * Base class for InternalFactories that are used by Providers, to handle
 * circular dependencies.
 *
 * @author sameb@google.com (Sam Berlin)
 */
abstract class ProviderInternalFactory<T> implements InternalFactory<T> {
  
  private final ProvisionListenerStackCallback<T> provisionCallback;
  private final boolean allowProxy;
  protected final Object source;
  
  ProviderInternalFactory(Object source, boolean allowProxy,
      ProvisionListenerStackCallback<T> provisionCallback) {
    this.provisionCallback = checkNotNull(provisionCallback, "provisionCallback");
    this.source = checkNotNull(source, "source");
    this.allowProxy = allowProxy;
  }
  
  protected T circularGet(final Provider<? extends T> provider, final Errors errors,
      InternalContext context, final Dependency<?> dependency, boolean linked)
      throws ErrorsException {    
    Class<?> expectedType = dependency.getKey().getTypeLiteral().getRawType();
    final ConstructionContext<T> constructionContext = context.getConstructionContext(this);
    
    // We have a circular reference between constructors. Return a proxy.
    if (constructionContext.isConstructing()) {
      if (!allowProxy) {
        throw errors.circularProxiesDisabled(expectedType).toException();
      } else {
        // TODO: if we can't proxy this object, can we proxy the other object?
        @SuppressWarnings("unchecked")
        T proxyType = (T) constructionContext.createProxy(errors, expectedType);
        return proxyType;
      }
    }

    // Optimization: Don't go through the callback stack if no one's listening.
    if (!provisionCallback.hasListeners()) {
      return provision(provider, errors, dependency, constructionContext);
    } else {
      return provisionCallback.provision(errors, context, new ProvisionCallback<T>() {
        public T call() throws ErrorsException {
          return provision(provider, errors, dependency, constructionContext);
        }
      });
    }
  }

  /**
   * Provisions a new instance. Subclasses should override this to catch
   * exceptions & rethrow as ErrorsExceptions.
   */
  protected T provision(Provider<? extends T> provider, Errors errors, Dependency<?> dependency,
      ConstructionContext<T> constructionContext) throws ErrorsException {
    constructionContext.startConstruction();
    try {
      T t = errors.checkForNull(provider.get(), source, dependency);
      constructionContext.setProxyDelegates(t);
      return t;
    } finally {
      constructionContext.finishConstruction();
    }
  }
}
