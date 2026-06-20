variable "subscription_id" {
  type        = string
  description = "Azure subscription ID to deploy into."
}

variable "location" {
  type    = string
  default = "eastus2"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "project" {
  type    = string
  default = "parley"
}

variable "image_tag" {
  type        = string
  description = "Container image tag to deploy (e.g. latest, or a git SHA)."
  default     = "latest"
}

variable "min_replicas" {
  type        = number
  description = "Minimum Container App replicas. Use 0 in dev (scale-to-zero, ~$0 idle). Use 1 in prod to avoid cold-start webhook timeouts."
  default     = 0
}

variable "max_replicas" {
  type        = number
  description = "Maximum Container App replicas under load."
  default     = 2
}

# --- Secrets: set these as HCP workspace variables on parley-app, not in git ---
variable "twilio_account_sid" {
  type        = string
  description = "Twilio Account SID."
  sensitive   = true
}

variable "openai_key_secret_id" {
  type        = string
  description = "Key Vault secret ID (versionless URI) holding the Azure OpenAI API key."
}

variable "twilio_token_secret_id" {
  type        = string
  description = "Key Vault secret ID (versionless URI) holding the Twilio auth token."
}

variable "mongodb_uri_secret_id" {
  type        = string
  description = "Key Vault secret ID (versionless URI) holding the MongoDB connection string (Atlas or Cosmos)."
}
