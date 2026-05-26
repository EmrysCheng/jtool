package io.github.emryschegn.jtool.progressbar;

import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 零依赖、线程安全的控制台进度条。
 *
 * <p>通过 {@code \r} 回到行首覆盖同一行实现原地刷新动画，适合在终端中展示耗时操作进度。
 * 支持 try-with-resources 自动完成、CAS 节流、自定义字符集、禁用模式等。
 *
 * <h3>帧格式</h3>
 * <pre> {@code
 * \r[██████████░░░░░░░░░░] 50% | 50/100 | 3s | processing item-50
 * }</pre>
 * 字段依次为：进度条 | 百分比 | raw/total | 已耗时 | 自定义消息
 *
 * <h3>基本用法</h3>
 * <pre> {@code
 * try (ProgressBar bar = ProgressBar.builder(list.size()).build()) {
 *     for (Item item : list) {
 *         process(item);
 *         bar.setMessage(item.name());
 *         bar.step();
 *     }
 * }
 * }</pre>
 *
 * @see Builder
 */
public final class ProgressBar implements AutoCloseable {

    /**
     * 总任务数
     */
    private final long total;

    /**
     * 进度条字符宽度（仅 █/░ 部分的字符数）
     */
    private final int width;

    /**
     * 最小重绘间隔（毫秒），0 表示每次 update 都重绘
     */
    private final long throttleMillis;

    /**
     * 已完成段填充字符
     */
    private final char filledChar;

    /**
     * 未完成段填充字符
     */
    private final char emptyChar;

    /**
     * 输出流，默认 System.out
     */
    private final PrintStream out;

    /**
     * false 时所有公开方法变为 no-op，不产生任何输出
     */
    private final boolean enabled;

    /**
     * 是否在帧末尾显示已耗时秒数
     */
    private final boolean showElapsed;

    /**
     * 创建时间戳（纳秒），用于计算已耗时
     */
    private final long startNanos;

    /**
     * 已完成的计数，AtomicLong 保证并发安全
     */
    private final AtomicLong current = new AtomicLong(0);

    /**
     * 上次渲染的时间戳（纳秒），CAS 节流闸门使用
     */
    private final AtomicLong lastRenderNanos = new AtomicLong(0);

    /**
     * 是否已调用 finish()，CAS 保证幂等
     */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    /**
     * 附加消息，volatile 保证多线程可见性
     */
    private volatile String message = "";

    private ProgressBar(Builder b) {
        this.total = b.total;
        this.width = b.width;
        this.throttleMillis = b.throttleMillis;
        this.filledChar = b.filledChar;
        this.emptyChar = b.emptyChar;
        this.out = b.out;
        this.enabled = b.enabled;
        this.showElapsed = b.showElapsed;
        this.startNanos = System.nanoTime();
    }

    /**
     * 推进 1 个单位，等价于 {@code update(1)}
     */
    public void step() {
        update(1L);
    }

    /**
     * 推进指定增量并可能触发重绘。
     *
     * <p>线程安全，可通过多线程并发调用。finish 后调用无效。
     *
     * @param delta 增量，必须 >= 0；0 时静默跳过，负数抛 IAE
     */
    public void update(long delta) {
        if (!enabled) {
            return;
        }
        if (finished.get()) {
            return;
        }
        if (delta == 0L) {
            return;
        }
        if (delta < 0L) {
            throw new IllegalArgumentException("delta must be >= 0");
        }
        current.addAndGet(delta);
        maybeRender();
    }

    /**
     * 设置帧末尾的附加消息，null 或空字符串表示清除。
     *
     * <p>可在任意线程调用，下一帧渲染时生效。
     */
    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    /**
     * 强制渲染最终帧并输出换行，之后所有 update 调用无效。
     *
     * <p>CAS 保证幂等，多次调用仅第一次生效。
     *
     * <p>即使处于节流间隔内也会强制渲染。
     */
    public void finish() {
        if (!enabled) {
            return;
        }
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        render();
        synchronized (out) {
            out.println();
            out.flush();
        }
    }

    /**
     * AutoCloseable 实现，等价于 {@link #finish()}，支持 try-with-resources
     */
    @Override
    public void close() {
        finish();
    }

    /**
     * 立即渲染当前进度到输出流，不受节流限制。
     *
     * <p>输出格式：{@code \r[███░░░] 30% | 3/10 | 1s | msg}
     *
     * <p>通过 {@code synchronized(out)} 保证 print + flush 原子性。
     */
    public void render() {
        if (!enabled) {
            return;
        }
        long raw = current.get();
        long done = Math.min(raw, total);
        final int percent = total == 0L ? 100 : (int) (done * 100L / total);
        int filled = total == 0L ? width : (int) (done * (long) width / total);
        StringBuilder sb = new StringBuilder(80);
        sb.append('\r').append('[');
        repeatAppend(sb, filledChar, Math.max(0, filled));
        repeatAppend(sb, emptyChar, Math.max(0, width - filled));
        sb.append("] ").append(percent).append("% | ").append(raw).append('/').append(total);
        if (showElapsed) {
            long elapsedSec = (System.nanoTime() - startNanos) / 1_000_000_000L;
            sb.append(" | ").append(elapsedSec).append('s');
        }
        String msg = message;
        if (!msg.isEmpty()) {
            sb.append(" | ").append(msg);
        }
        synchronized (out) {
            out.print(sb);
            out.flush();
        }
    }

    /**
     * CAS 节流闸门：仅在距上次渲染超过 throttleMillis 时才调用 render()。
     *
     * <p>throttleMillis == 0 时跳过节流，每次都渲染。
     */
    private void maybeRender() {
        if (throttleMillis == 0L) {
            render();
            return;
        }
        long now = System.nanoTime();
        long last = lastRenderNanos.get();
        if (now - last < throttleMillis * 1_000_000L) {
            return;
        }
        if (!lastRenderNanos.compareAndSet(last, now)) {
            return;
        }
        render();
    }

    // 兼容 JDK 8：替代 String.repeat()（JDK 11+）
    private static void repeatAppend(StringBuilder sb, char c, int count) {
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
    }

    /**
     * 创建 Builder 实例。
     *
     * @param total 总任务数，>= 0；为 0 时渲染为 100%
     */
    public static Builder builder(long total) {
        Builder b = new Builder();
        b.total = total;
        return b;
    }

    /**
     * 进度条构建器，所有参数都有合理默认值，只需指定 total 即可使用。
     *
     * <p>参数校验集中在 {@link #build()} 中，失败时抛出 unchecked 异常。
     */
    public static final class Builder {
        long total;
        int width = 20;
        long throttleMillis = 80L;
        char filledChar = '█';
        char emptyChar = '░';
        PrintStream out = System.out;
        boolean enabled = true;
        boolean showElapsed = true;

        Builder() {
        }

        /**
         * 进度条字符宽度，默认 20，必须 >= 1
         */
        public Builder width(int width) {
            this.width = width;
            return this;
        }

        /**
         * 最小重绘间隔（毫秒），默认 80ms；设为 0 关闭节流
         */
        public Builder throttleMillis(long millis) {
            this.throttleMillis = millis;
            return this;
        }

        /**
         * 已完成段填充字符，默认 '█'（U+2588）
         */
        public Builder filledChar(char c) {
            this.filledChar = c;
            return this;
        }

        /**
         * 未完成段填充字符，默认 '░'（U+2591）
         */
        public Builder emptyChar(char c) {
            this.emptyChar = c;
            return this;
        }

        /**
         * 输出流，默认 System.out；可注入 ByteArrayOutputStream 用于测试
         */
        public Builder out(PrintStream out) {
            this.out = out;
            return this;
        }

        /**
         * 是否启用进度条，默认 true；false 时所有方法变 no-op
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 是否显示已耗时秒数，默认 true
         */
        public Builder showElapsed(boolean show) {
            this.showElapsed = show;
            return this;
        }

        /**
         * 构建 ProgressBar 实例，校验所有参数合法性。
         *
         * @throws IllegalArgumentException total &lt; 0, width &lt; 1, throttleMillis &lt; 0
         * @throws NullPointerException     out 为 null
         */
        public ProgressBar build() {
            if (total < 0) {
                throw new IllegalArgumentException("total must be >= 0");
            }
            if (width < 1) {
                throw new IllegalArgumentException("width must be >= 1");
            }
            if (throttleMillis < 0) {
                throw new IllegalArgumentException("throttleMillis must be >= 0");
            }
            Objects.requireNonNull(out, "out");
            return new ProgressBar(this);
        }
    }
}