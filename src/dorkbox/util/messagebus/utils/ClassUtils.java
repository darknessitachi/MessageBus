/*
 * Copyright 2015 dorkbox, llc
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
package dorkbox.util.messagebus.utils;

import com.esotericsoftware.kryo.util.IdentityMap;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final
class ClassUtils {

    private volatile IdentityMap<Class<?>, Class<?>> arrayCache;
    private volatile IdentityMap<Class<?>, Class<?>[]> superClassesCache;

    // Recommended for best performance while adhering to the "single writer principle". Must be static-final
    private static final AtomicReferenceFieldUpdater<ClassUtils, IdentityMap> arrayREF =
                    AtomicReferenceFieldUpdater.newUpdater(ClassUtils.class,
                                                           IdentityMap.class,
                                                           "arrayCache");

    private static final AtomicReferenceFieldUpdater<ClassUtils, IdentityMap> superClassesREF =
                    AtomicReferenceFieldUpdater.newUpdater(ClassUtils.class,
                                                           IdentityMap.class,
                                                           "superClassesCache");

    /**
     * These data structures are never reset because the class hierarchy doesn't change at runtime. This class uses the "single writer
     * principle" for storing data, EVEN THOUGH it's not accessed by a single writer. This DOES NOT MATTER because duplicates DO NOT matter
     */
    public
    ClassUtils(final float loadFactor) {
        this.arrayCache = new IdentityMap<Class<?>, Class<?>>(32, loadFactor);
        this.superClassesCache = new IdentityMap<Class<?>, Class<?>[]>(32, loadFactor);
    }

    /**
     * if parameter clazz is of type array, then the super classes are of array type as well
     * <p>
     * race conditions will result in DUPLICATE answers, which we don't care if happens
     * never returns null
     * never reset (class hierarchy never changes during runtime)
     */
    public
    Class<?>[] getSuperClasses(final Class<?> clazz) {
        // access a snapshot of the subscriptions (single-writer-principle)
        final IdentityMap<Class<?>, Class<?>[]> cache = superClassesREF.get(this);

        Class<?>[] classes = cache.get(clazz);

        // duplicates DO NOT MATTER
        if (classes == null) {
            // publish all super types of class
            final Class<?>[] superTypes = ReflectionUtils.getSuperTypes(clazz);
            final int length = superTypes.length;

            final ArrayList<Class<?>> newList = new ArrayList<Class<?>>(length);

            Class<?> c;
            final boolean isArray = clazz.isArray();

            if (isArray) {
                for (int i = 0; i < length; i++) {
                    c = superTypes[i];

                    c = getArrayClass(c);

                    if (c != clazz) {
                        newList.add(c);
                    }
                }
            }
            else {
                for (int i = 0; i < length; i++) {
                    c = superTypes[i];

                    if (c != clazz) {
                        newList.add(c);
                    }
                }
            }

            classes = new Class<?>[newList.size()];
            newList.toArray(classes);
            cache.put(clazz, classes);

            // save this snapshot back to the original (single writer principle)
            superClassesREF.lazySet(this, cache);
        }

        return classes;
    }

    /**
     * race conditions will result in DUPLICATE answers, which we don't care if happens
     * never returns null
     * never resets (class hierarchy never changes during runtime)
     *
     * https://bugs.openjdk.java.net/browse/JDK-6525802  (fixed this in 2007, so Array.newInstance is just as fast (via intrinsics) new [])
     * Cache is in place to keep GC down.
     */
    public
    Class<?> getArrayClass(final Class<?> c) {
        // access a snapshot of the subscriptions (single-writer-principle)
        final IdentityMap<Class<?>, Class<?>> cache = arrayREF.get(this);

        Class<?> clazz = cache.get(c);

        if (clazz == null) {
            // messy, but the ONLY way to do it. Array super types are also arrays
            final Object[] newInstance = (Object[]) Array.newInstance(c, 0);
            clazz = newInstance.getClass();
            cache.put(c, clazz);

            // save this snapshot back to the original (single writer principle)
            arrayREF.lazySet(this, cache);
        }

        return clazz;
    }


    /**
     * Clears the caches, should only be called on shutdown
     */
    public
    void shutdown() {
        this.arrayCache.clear();
        this.superClassesCache.clear();
    }

    public static
    <T> ArrayList<T> findCommon(final T[] arrayOne, final T[] arrayTwo) {

        T[] arrayToHash;
        T[] arrayToSearch;

        final int size1 = arrayOne.length;
        final int size2 = arrayTwo.length;

        final int hashSize;
        final int searchSize;

        if (size1 < size2) {
            hashSize = size1;
            searchSize = size2;
            arrayToHash = arrayOne;
            arrayToSearch = arrayTwo;
        }
        else {
            hashSize = size2;
            searchSize = size1;
            arrayToHash = arrayTwo;
            arrayToSearch = arrayOne;
        }


        final ArrayList<T> intersection = new ArrayList<T>(searchSize);

        final HashSet<T> hashedArray = new HashSet<T>();
        for (int i = 0; i < hashSize; i++) {
            T t = arrayToHash[i];
            hashedArray.add(t);
        }

        for (int i = 0; i < searchSize; i++) {
            T t = arrayToSearch[i];
            if (hashedArray.contains(t)) {
                intersection.add(t);
            }
        }

        return intersection;
    }

    public static
    <T> ArrayList<T> findCommon(final ArrayList<T> arrayOne, final ArrayList<T> arrayTwo) {

        ArrayList<T> arrayToHash;
        ArrayList<T> arrayToSearch;

        final int size1 = arrayOne.size();
        final int size2 = arrayTwo.size();

        final int hashSize;
        final int searchSize;

        if (size1 < size2) {
            hashSize = size1;
            searchSize = size2;
            arrayToHash = arrayOne;
            arrayToSearch = arrayTwo;
        }
        else {
            hashSize = size2;
            searchSize = size1;
            arrayToHash = arrayTwo;
            arrayToSearch = arrayOne;
        }

        ArrayList<T> intersection = new ArrayList<T>(searchSize);

        HashSet<T> hashedArray = new HashSet<T>();
        for (int i = 0; i < hashSize; i++) {
            T t = arrayToHash.get(i);
            hashedArray.add(t);
        }

        for (int i = 0; i < searchSize; i++) {
            T t = arrayToSearch.get(i);
            if (hashedArray.contains(t)) {
                intersection.add(t);
            }
        }

        return intersection;
    }
}