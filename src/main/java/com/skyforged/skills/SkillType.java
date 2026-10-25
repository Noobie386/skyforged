package com.skyforged.skills;

public enum SkillType {
	COMBAT("Combat"),
	MINING("Mining"),
	VOICE("Voice");

	private final String displayName;

	SkillType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
