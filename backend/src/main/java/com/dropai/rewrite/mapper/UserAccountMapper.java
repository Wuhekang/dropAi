package com.dropai.rewrite.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dropai.rewrite.entity.UserAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserAccountMapper extends BaseMapper<UserAccount> {
    @Select("""
            SELECT *
            FROM user_account
            WHERE phone = #{phone}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    UserAccount selectActiveByPhoneForUpdate(@Param("phone") String phone);

    @Select("""
            SELECT *
            FROM user_account
            WHERE id = #{userId}
              AND deleted_at IS NULL
            FOR UPDATE
            """)
    UserAccount selectActiveByIdForUpdate(@Param("userId") Long userId);

    @Update("""
            UPDATE user_account
            SET password_hash = #{passwordHash},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND deleted_at IS NULL
            """)
    int updatePasswordHash(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE user_account
            SET school_id = #{schoolId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND deleted_at IS NULL
              AND UPPER(role) = 'USER'
            """)
    int updateSchoolId(@Param("userId") Long userId, @Param("schoolId") Long schoolId);

    @Update("""
            UPDATE user_account
            SET points = points - #{cost},
                used_points = used_points + #{cost},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND deleted_at IS NULL
              AND account_enabled = TRUE
              AND points >= #{cost}
            """)
    int deductPoints(@Param("userId") Long userId, @Param("cost") int cost);

    @Update("""
            UPDATE user_account
            SET points = points - #{points},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND deleted_at IS NULL
              AND account_enabled = TRUE
              AND points >= #{points}
            """)
    int transferOutPoints(@Param("userId") Long userId, @Param("points") int points);

    @Update("""
            UPDATE user_account
            SET points = points + #{points},
                total_points = total_points + #{points},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND deleted_at IS NULL
              AND account_enabled = TRUE
            """)
    int addPoints(@Param("userId") Long userId, @Param("points") int points);

    @Update("""
            UPDATE user_account
            SET points = points + #{points},
                used_points = CASE WHEN used_points >= #{points} THEN used_points - #{points} ELSE 0 END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
              AND deleted_at IS NULL
              AND account_enabled = TRUE
            """)
    int refundPoints(@Param("userId") Long userId, @Param("points") int points);

    @Update("""
            UPDATE user_account
            SET last_notice_time = CURRENT_TIMESTAMP,
                notice_read_id = #{noticeId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
            """)
    int markNoticeRead(@Param("userId") Long userId, @Param("noticeId") Long noticeId);

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
