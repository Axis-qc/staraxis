package staraxis.core.rng;

/**
 * 32 位确定性随机数生成器，单线程使用。实现基于 XorShift32，
 * 提供可序列化的种子以便回放与对账。
 */
public final class DetRand32 {
  private int state;

  public DetRand32(int seed) {
    if (seed == 0) seed = 0x9E3779B9; // 避免全零
    this.state = seed;
  }

  /** 返回一个 [0, 2^32) 的无符号随机数。*/
  public int nextInt() {
    int x = state;
    x ^= x << 13;
    x ^= x >>> 17;
    x ^= x << 5;
    state = x;
    return x;
  }

  /** 返回 [0, bound) 的随机数。要求 bound 为正 */
  public int nextInt(int bound) {
    return (nextInt() >>> 1) % bound; // 去掉符号位再取模
  }

  public int getState() {return state;}
}
