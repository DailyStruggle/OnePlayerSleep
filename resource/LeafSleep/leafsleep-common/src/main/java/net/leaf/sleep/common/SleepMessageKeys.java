package net.leaf.sleep.common;

/**
 * Schema keys for weighted sleep and wakeup message files under {@code messages/*.yml}, loaded via
 * {@link io.github.dailystruggle.rtp.common.configuration.MultiConfigParser} ({@code LeafSleep
 * ADR-002}).
 *
 * @governedBy ADR-002
 */
public enum SleepMessageKeys {
  version,
  weight,
  sleepText,
  wakeupText,
  cancelPrompt,
  sleepPrompt
}
