/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;
import java.io.Serializable;

/**
 * One or more variables that together maintain an initially zero
 * {@code long} sum.  When updates (method {@link #add}) are contended
 * across threads, the set of variables may grow dynamically to reduce
 * contention. Method {@link #sum} (or, equivalently, {@link
 * #longValue}) returns the current total combined across the
 * variables maintaining the sum.
 *
 * <p>This class is usually preferable to {@link AtomicLong} when
 * multiple threads update a common sum that is used for purposes such
 * as collecting statistics, not for fine-grained synchronization
 * control.  Under low update contention, the two classes have similar
 * characteristics. But under high contention, expected throughput of
 * this class is significantly higher, at the expense of higher space
 * consumption.
 *
 * <p>LongAdders can be used with a {@link
 * java.util.concurrent.ConcurrentHashMap} to maintain a scalable
 * frequency map (a form of histogram or multiset). For example, to
 * add a count to a {@code ConcurrentHashMap<String,LongAdder> freqs},
 * initializing if not already present, you can use {@code
 * freqs.computeIfAbsent(k -> new LongAdder()).increment();}
 *
 * <p>This class extends {@link Number}, but does <em>not</em> define
 * methods such as {@code equals}, {@code hashCode} and {@code
 * compareTo} because instances are expected to be mutated, and so are
 * not useful as collection keys.
 *
 * @since 1.8
 * @author Doug Lea
 */
public class LongAdder extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    /**
     * Creates a new adder with initial sum of zero.
     */
    public LongAdder() {
    }

    /**
     * Adds the given value.
     *
     * @param x the value to add
     */
    public void add(long x) {
        // as是Striped64中的成员变量cells数组
        // b是Striped64中的成员变量base，即基数值
        // v是当前线程hash到cell中存储的值
        // m是cells数组长度减1，hash时做掩码使用
        // a当前线程hash得到的cells数组中的cell元素
        Cell[] as;
        long b, v;
        int m;
        Cell a;

        // true 则说明出现过线程竞争
        // 条件1：cell数组是否为空，首次首线程进来肯定为空，false。出现过线程竞争，创建了cells数组则不为空。
        // 条件2：CAS更新base基数，出现线程竞争的话，CAS更新失败。进入if代码块中
        if ((as = cells) != null || !casBase(b = base, b + x)) {

            // true 线程无竞争； false线程竞争激烈，多个线程hash到同一个cell，可能需要扩容
            boolean uncontended = true;

            // 条件1：cell为空，说明出现线程竞争，与外层if判断的条件2相关
            // 条件2：cells数组长度减1小于0，应该不会出现
            // 条件3：当前线程hash得到的cells数组中的元素cell对象为空，说明当前当前线程还没有更新过cell，应该初始化一个cell。
            //       getProbe()，返回线程中的threadLocalRandomProbe字段
            //       它是通过随机数生成的一个值，对于一个确定的线程这个值是固定的（除非刻意修改过它）
            // 条件4：CAS更新当前线程所命中的cell的值，失败则表明当前线程竞争很激烈，应当考虑扩容
            if (as == null || (m = as.length - 1) < 0 ||
                (a = as[getProbe() & m]) == null ||
                !(uncontended = a.cas(v = a.value, v + x)))

                // 调用Striped64中的方法处理
                longAccumulate(x, null, uncontended);
        }
    }

    /**
     * Equivalent to {@code add(1)}.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Equivalent to {@code add(-1)}.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * Returns the current sum.  The returned value is <em>NOT</em> an
     * atomic snapshot; invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the sum is being calculated might not be
     * incorporated.
     *
     * @return the sum
     */
    public long sum() {
        Cell[] as = cells; Cell a;
        long sum = base;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    public void reset() {
        Cell[] as = cells; Cell a;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    a.value = 0L;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the sum
     */
    public long sumThenReset() {
        Cell[] as = cells; Cell a;
        long sum = base;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     * @return the String representation of the {@link #sum}
     */
    public String toString() {
        return Long.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     *
     * @return the sum
     */
    public long longValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a narrowing
     * primitive conversion.
     */
    public int intValue() {
        return (int)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code double} after a widening
     * primitive conversion.
     */
    public double doubleValue() {
        return (double)sum();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     * @serial include
     */
    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Return a {@code LongAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code LongAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     *
     * @return a {@link SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
