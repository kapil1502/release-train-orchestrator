package io.github.kapil1502.releasetrain.dto;

import java.util.List;

public record Strategy(
		List<RolloutStep> steps
) {}
