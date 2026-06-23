#!/usr/bin/env bash
# Load KEY=value lines from a dotenv file without shell-eval (safe for & and spaces in values).
load_dotenv() {
	local file="$1"
	[[ -f "$file" ]] || return 0
	while IFS= read -r line || [[ -n "$line" ]]; do
		line="${line%%$'\r'}"
		[[ "$line" =~ ^[[:space:]]*# ]] && continue
		[[ "$line" =~ ^[[:space:]]*$ ]] && continue
		[[ "$line" == *=* ]] || continue
		local key="${line%%=*}"
		local val="${line#*=}"
		key="${key#"${key%%[![:space:]]*}"}"
		key="${key%"${key##*[![:space:]]}"}"
		export "${key}=${val}"
	done <"$file"
}
