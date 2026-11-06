CREATE DATABASE IF NOT EXISTS `${project.databaseName}` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `${project.databaseName}`;
CREATE TABLE IF NOT EXISTS `sys_user` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`username` VARCHAR(64) NOT NULL UNIQUE,`password_hash` VARCHAR(100) NOT NULL,`status` TINYINT(1) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS `sys_role` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`code` VARCHAR(64) NOT NULL UNIQUE,`name` VARCHAR(100) NOT NULL);
CREATE TABLE IF NOT EXISTS `sys_permission` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`code` VARCHAR(100) NOT NULL UNIQUE,`name` VARCHAR(100) NOT NULL);
CREATE TABLE IF NOT EXISTS `sys_menu` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`code` VARCHAR(64) NOT NULL UNIQUE,`name` VARCHAR(100) NOT NULL,`path` VARCHAR(200) NOT NULL,`permission_code` VARCHAR(100));
<#list entities as entity>CREATE TABLE IF NOT EXISTS `${entity.tableName}` (<#list entity.fields as field>`${field.columnName}` ${field.sqlType}<#if field.name == "id"> PRIMARY KEY AUTO_INCREMENT</#if><#if field.required && field.name != "id"> NOT NULL</#if><#if field.name == "deleted"> DEFAULT 0</#if><#sep>,</#sep></#list><#if entity.parentRelation??>,INDEX `idx_${entity.tableName}_${entity.parentRelation.foreignKeyColumn}` (`${entity.parentRelation.foreignKeyColumn}`),CONSTRAINT `fk_${entity.tableName}_${entity.parentRelation.sourceTable}` FOREIGN KEY (`${entity.parentRelation.foreignKeyColumn}`) REFERENCES `${entity.parentRelation.sourceTable}` (`id`) ON DELETE ${entity.parentRelation.onDelete}</#if>);
</#list>INSERT IGNORE INTO `sys_user` (`username`,`password_hash`) VALUES ('admin','$2a$10$7EqJtq98hPqEX7fNZaFWoO5FO5D7YQhJBPKACfh3eD7hQ2yPV5E3S');
INSERT IGNORE INTO `sys_role` (`code`,`name`) VALUES ('ADMIN','超级管理员');
<#list permissions as p>INSERT IGNORE INTO `sys_permission` (`code`,`name`) VALUES ('${p.code}','${p.description}');
</#list><#list entities as entity>INSERT IGNORE INTO `sys_menu` (`code`,`name`,`path`,`permission_code`) VALUES ('${entity.variableName}','${entity.title}管理','/${entity.variableName}','${entity.variableName}:view');
</#list>
