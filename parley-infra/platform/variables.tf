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

variable "openai_deployment_name" {
  type        = string
  description = "Azure OpenAI deployment name (Spring AI deployment-name)."
  default     = "gpt-4.1-mini"
}

variable "openai_model_name" {
  type        = string
  description = "Azure OpenAI model name."
  default     = "gpt-4.1-mini"
}

variable "openai_model_version" {
  type        = string
  description = "Azure OpenAI model version. Verify in your region: az cognitiveservices account list-models."
  default     = "2025-04-14"
}

variable "openai_deployment_sku" {
  type        = string
  description = "Deployment SKU. Use Standard in eastus2 unless GlobalStandard quota is approved."
  default     = "Standard"
}

variable "openai_capacity" {
  type        = number
  description = "Tokens-per-minute capacity (in thousands) for the deployment."
  default     = 10
}

variable "log_analytics_daily_quota_gb" {
  type        = number
  description = "Daily ingestion cap (GB) for Log Analytics. Prevents log surprise-billing."
  default     = 0.5
}

variable "log_analytics_retention_days" {
  type        = number
  description = "Log Analytics retention in days."
  default     = 30
}
