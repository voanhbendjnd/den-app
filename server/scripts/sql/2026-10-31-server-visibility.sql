-- Select the intended DenHub database. Preview first; set @ApplyChanges = 1 after review.
-- Run before deploying code that maps servers.visibility. No automatic startup hook.
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @ApplyChanges bit = 0;
DECLARE @TableId int = OBJECT_ID(N'dbo.servers', N'U');
IF @TableId IS NULL
    THROW 51000, 'Expected dbo.servers in the selected database.', 1;

SELECT DB_NAME() AS database_name, @ApplyChanges AS apply_changes;
IF COL_LENGTH(N'dbo.servers', N'visibility') IS NOT NULL
BEGIN
    -- Do not silently reinterpret an existing column or overwrite configured visibility.
    SELECT name, TYPE_NAME(user_type_id) AS type_name, max_length, is_nullable
    FROM sys.columns WHERE object_id = @TableId AND name = N'visibility';
    PRINT 'visibility already exists. Review its type, data, default and constraint manually; no changes applied.';
    RETURN;
END;

DECLARE @Sql nvarchar(max) = N'ALTER TABLE dbo.servers ADD visibility NVARCHAR(7) NOT NULL
    CONSTRAINT DF_servers_visibility DEFAULT N''PRIVATE'' WITH VALUES;';
-- Compile the CHECK only after the new column exists; SQL Server resolves columns per batch.
DECLARE @CheckSql nvarchar(max) = N'ALTER TABLE dbo.servers ADD CONSTRAINT CK_servers_visibility
    CHECK (visibility IN (N''PUBLIC'', N''PRIVATE''));';
SELECT @Sql AS planned_sql, @CheckSql AS planned_constraint_sql;
IF @ApplyChanges = 0 RETURN;

BEGIN TRY
    BEGIN TRANSACTION;
    EXEC sys.sp_executesql @Sql;
    EXEC sys.sp_executesql @CheckSql;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
