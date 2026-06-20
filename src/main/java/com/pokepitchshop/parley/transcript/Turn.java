package com.pokepitchshop.parley.transcript;

import java.time.Instant;

public record Turn(Instant at, String caller, String agent) {
}
