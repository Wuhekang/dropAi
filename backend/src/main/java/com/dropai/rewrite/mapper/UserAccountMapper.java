package com.dropai.rewrite.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dropai.rewrite.entity.UserAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Update("""
            UPDATE user_account
            SET points = points - #{cost},
                used_points = used_points + #{cost},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND points >= #{cost}
            """)
    int deductPoints(@Param("userId") Long userId, @Param("cost") int cost);

    @Update("""
            UPDATE user_account
            SET points = #{points},
                total_points = #{totalPoints},
                used_points = #{usedPoints},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
            """)
    int updatePointSnapshot(@Param("userId") Long userId,
                            @Param("points") int points,
                            @Param("totalPoints") int totalPoints,
                            @Param("usedPoints") int usedPoints);
}
