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

variable "openai_model_version" {
  type        = string
  description = "Azure OpenAI gpt-4o-mini model version. Verify the current value in your region."
  default     = "2024-07-18"
}

variable "openai_capacity" {
  type        = string
  description = "Tokens-per-minute capacity (in thousands) for the deployment."
  default     = 10
}
