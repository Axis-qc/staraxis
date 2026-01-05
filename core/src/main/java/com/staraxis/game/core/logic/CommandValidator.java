package com.staraxis.game.core.logic;

import com.staraxis.game.shared.model.Command;
import com.staraxis.game.shared.model.GameState;

/**
 * 指令校验器 (Command Validator)
 *
 * 提供的接口: 校验客户端指令的合法性 (FR-007)
 */
public class CommandValidator /* 指令校验器 */ {

    /**
     * 校验指令 (Validate Command)
     */
    public boolean validate /* 校验 */(GameState state, Command command) {
        if (command == null) {
            return false;
        }

        switch (command.commandType) {
            case MOVE:
                return validateMove /* 校验移动 */(state, command);
            case BUILD:
                return validateBuild /* 校验建造 */(state, command);
            default:
                return true;
        }
    }

    private boolean validateMove /* 校验移动 */(GameState state, Command command) {
        // TODO: 检查移动速度、权限等 (Check speed, authority, etc.)
        return true;
    }

    private boolean validateBuild /* 校验建造 */(GameState state, Command command) {
        // TODO: 检查资源、距离等 (Check resources, distance, etc.)
        return true;
    }
}
