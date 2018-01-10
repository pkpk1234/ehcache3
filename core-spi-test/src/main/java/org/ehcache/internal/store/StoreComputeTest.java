/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehcache.internal.store;

import org.ehcache.core.exceptions.StorePassThroughException;
import org.ehcache.core.spi.store.StoreAccessException;
import org.ehcache.core.spi.store.Store;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.internal.TestExpiries;
import org.ehcache.internal.TestTimeSource;
import org.ehcache.spi.loaderwriter.CacheLoadingException;
import org.ehcache.spi.test.After;
import org.ehcache.spi.test.LegalSPITesterException;
import org.ehcache.spi.test.SPITest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import org.junit.Assert;

import java.time.Duration;
import java.util.function.BiFunction;

public class StoreComputeTest<K, V> extends SPIStoreTester<K, V> {

  public StoreComputeTest(StoreFactory<K, V> factory) {
    super(factory);
  }

  protected Store<K, V> kvStore;
  protected Store kvStore2;

  @After
  public void tearDown() {
    if (kvStore != null) {
      factory.close(kvStore);
      kvStore = null;
    }
    if (kvStore2 != null) {
      @SuppressWarnings("unchecked")
      Store<K, V> kvStore2 = this.kvStore2;
      factory.close(kvStore2);
      this.kvStore2 = null;
    }
  }

  @SuppressWarnings("unchecked")
  @SPITest
  public void testWrongReturnValueType() throws Exception {
    kvStore = factory.newStore();

    if (factory.getValueType() == Object.class) {
      Assert.fail("Warning, store uses Object as value type, cannot verify in this configuration");
    }

    final Object value;
    if (factory.getValueType() == String.class) {
      value = this;
    } else {
      value = "value";
    }

    final K key = factory.createKey(13);
    try {
      kvStore.compute(key, (BiFunction) (key1, oldValue) -> {
        return value; // returning wrong value type from function
      });
      throw new AssertionError();
    } catch (ClassCastException e) {
      // expected
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }

  @SuppressWarnings("unchecked")
  @SPITest
  public void testWrongKeyType() throws Exception {
    kvStore2 = factory.newStore();

    if (factory.getKeyType() == Object.class) {
      System.err.println("Warning, store uses Object as key type, cannot verify in this configuration");
      return;
    }

    final Object key;
    if (factory.getKeyType() == String.class) {
      key = this;
    } else {
      key = "key";
    }

    try {
      // wrong key type
      kvStore2.compute(key, (key1, oldValue) -> {
        throw new AssertionError();
      });
      throw new AssertionError();
    } catch (ClassCastException e) {
      // expected
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }

  @SPITest
  public void testComputePutsValueInStore() throws Exception {
    kvStore = factory.newStore();

    final K key = factory.createKey(14);
    final V value = factory.createValue(153);

    try {
      kvStore.compute(key, (keyParam, oldValue) -> value);
      assertThat(kvStore.get(key).get(), is(value));
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }

  @SPITest
  public void testOverwriteExitingValue() throws Exception {
    kvStore = factory.newStore();

    final K key = factory.createKey(151);
    final V value = factory.createValue(1525);
    final V value2 = factory.createValue(1526);

    assertThat(value2, not(equalTo(value)));

    try {
      kvStore.put(key, value);
      kvStore.compute(key, (keyParam, oldValue) -> value2);
      assertThat(kvStore.get(key).get(), is(value2));
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }

  @SPITest
  public void testNullReturnRemovesEntry() throws Exception {
    kvStore = factory.newStore();

    final K key = factory.createKey(1535603985);
    final V value = factory.createValue(15920835);

    try {
      kvStore.put(key, value);
      kvStore.compute(key, (keyParam, oldValue) -> null);
      assertThat(kvStore.get(key), nullValue());
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }

  @SPITest
  public void testException() throws Exception {
    kvStore = factory.newStore();

    final K key = factory.createKey(520928098);
    final V value = factory.createValue(15098209865L);

    final RuntimeException re = new RuntimeException();

    try {
      kvStore.put(key, value);
      assertThat(kvStore.get(key).get(), is(value));

      kvStore.compute(key, (keyParam, oldValue) -> {
        throw re;
      });
    } catch (RuntimeException e) {
      assertThat(e, is(re));
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }

    assertThat(kvStore.get(key).get(), is(value));
  }

  @SPITest
  public void testStorePassThroughException() throws Exception {
    kvStore = factory.newStore();

    K key = factory.createKey(520928098);
    V value = factory.createValue(15098209865L);

    RuntimeException exception = new RuntimeException("error");
    StorePassThroughException re = new StorePassThroughException(exception);

    try {
      kvStore.put(key, value);
      assertThat(kvStore.get(key).get(), is(value));

      kvStore.compute(key, (keyParam, oldValue) -> {
        throw re;
      });
    } catch (RuntimeException e) {
      assertThat(e, is(exception));
    }

    assertThat(kvStore.get(key).get(), is(value));
  }

  @SPITest
  public void testExceptionOnSupplier() throws Exception {
    kvStore = factory.newStore();

    K key = factory.createKey(520928098);
    V value = factory.createValue(15098209865L);

    RuntimeException re = new RuntimeException();

    try {
      kvStore.put(key, value);
      assertThat(kvStore.get(key).get(), is(value));

      kvStore.compute(key, (keyParam, oldValue) -> oldValue, () -> { throw re; });
    } catch (StoreAccessException e) {
      assertThat(e.getCause(), is(re));
    }

    assertThat(kvStore.get(key).get(), is(value));
  }

  @SPITest
  public void testPassThroughExceptionOnSupplier() throws Exception {
    kvStore = factory.newStore();

    K key = factory.createKey(520928098);
    V value = factory.createValue(15098209865L);

    RuntimeException exception = new RuntimeException("error");
    StorePassThroughException re = new StorePassThroughException(exception);

    try {
      kvStore.put(key, value);
      assertThat(kvStore.get(key).get(), is(value));

      kvStore.compute(key, (keyParam, oldValue) -> oldValue, () -> { throw re; });
    } catch (RuntimeException e) {
      assertThat(e, is(exception));
    }

    assertThat(kvStore.get(key).get(), is(value));
  }

  @SPITest
  public void testComputeExpiresOnAccess() throws Exception {
    TestTimeSource timeSource = new TestTimeSource(10042L);
    kvStore = factory.newStoreWithExpiry(TestExpiries.custom(ExpiryPolicy.INFINITE, Duration.ZERO, null), timeSource);

    final K key = factory.createKey(1042L);
    final V value = factory.createValue(1340142L);

    try {
      kvStore.put(key, value);

      Store.ValueHolder<V> result = kvStore.compute(key, (k, v) -> v, () -> false);
      assertThat(result.get(), is(value));
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }

  @SPITest
  public void testComputeExpiresOnUpdate() throws Exception {
    TestTimeSource timeSource = new TestTimeSource(10042L);
    kvStore = factory.newStoreWithExpiry(TestExpiries.custom(ExpiryPolicy.INFINITE, null, Duration.ZERO), timeSource);

    final K key = factory.createKey(1042L);
    final V value = factory.createValue(1340142L);
    final V newValue = factory.createValue(134054142L);

    try {
      kvStore.put(key, value);

      Store.ValueHolder<V> result = kvStore.compute(key, (k, v) -> newValue, () -> false);
      assertThat(result.get(), is(newValue));
    } catch (StoreAccessException e) {
      throw new LegalSPITesterException("Warning, an exception is thrown due to the SPI test");
    }
  }
}
