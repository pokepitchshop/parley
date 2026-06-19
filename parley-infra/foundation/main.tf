locals {
  prefix = "${var.project}-${var.environment}"
}

resource "azurerm_resource_group" "this" {
  name     = "${local.prefix}-rg"
  location = var.location
}

# The identity Parley runs as. The platform + app layers grant it access to
# Key Vault, Azure OpenAI, and ACR -- so the running app needs no stored keys.
resource "azurerm_user_assigned_identity" "app" {
  name                = "${local.prefix}-id"
  resource_group_name = azurerm_resource_group.this.name
  location            = azurerm_resource_group.this.location
}
