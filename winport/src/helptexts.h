// helptexts.h — the in-app help markdown, carried over from MainActivity.kt.
#pragma once
#include <string>

const std::string& foodsHelpText();
const std::string& eatenHelpText();
const std::string& graphHelpText();
const std::string& jsonHelpText();
const std::string& aiHelpText();
const std::string& utilitiesHelpText();
const std::string& insertHelpText();
std::string editHelpText(bool isLiquidFood);
std::string copyHelpText(bool isLiquidFood);
std::string buildRecipeHelpText(const std::string& screenTitle);
