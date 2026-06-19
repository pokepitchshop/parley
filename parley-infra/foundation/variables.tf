variable "subscription_id" {
  type        = string
  description = "Azure subscription ID to deploy into."
}

variable "location" {
  type        = string
  description = "Azure region. Use one with Azure OpenAI availability."
  default     = "eastus2"
}

variable "environment" {
  type        = string
  description = "Environment name (dev | staging | prod)."
  default     = "dev"
}

variable "project" {
  type        = string
  description = "Project short name, used in resource names."
  default     = "parley"
}
