package io.github.kapil1502.releasetrain.dto;

public record RolloutStep(
		int step,
		int percentage,
		int pauseDurationSeconds
) {}
