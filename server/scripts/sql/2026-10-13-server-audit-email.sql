-- Select the intended DenHub database before running this script.
-- Default: preview only. Review the database and ALTER statements, then set 1.
-- Expands only dbo.servers audit columns; preserves type, collation, nullability.
-- No startup hook; no other table, index or constraint is modified.
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @ApplyChanges bit = 0;
DECLARE @TableId int = OBJECT_ID(N'dbo.servers', N'U');
IF @TableId IS NULL
    THROW 51000, 'Expected dbo.servers in the selected database.', 1;

DECLARE @Columns TABLE (
    column_name sysname NOT NULL,
    type_name sysname NOT NULL,
    max_length smallint NOT NULL,
    collation_name sysname NULL,
    is_nullable bit NOT NULL,
    is_computed bit NOT NULL,
    is_user_defined bit NOT NULL
);
INSERT INTO @Columns
SELECT c.name, t.name, c.max_length, c.collation_name, c.is_nullable,
       c.is_computed, t.is_user_defined
FROM sys.columns AS c
JOIN sys.types AS t ON t.user_type_id = c.user_type_id
WHERE c.object_id = @TableId
  AND c.name IN (N'created_by', N'last_modified_by');

IF (SELECT COUNT(*) FROM @Columns) <> 2
    THROW 51001, 'Both servers audit columns must exist.', 1;
IF EXISTS (SELECT 1 FROM @Columns WHERE type_name NOT IN (N'varchar', N'nvarchar')
           OR is_computed = 1 OR is_user_defined = 1 OR collation_name IS NULL)
    THROW 51002, 'Unexpected audit column type or metadata. Review manually.', 1;

DECLARE @Statements TABLE (column_name sysname, sql_text nvarchar(max));
INSERT INTO @Statements
SELECT column_name,
       N'ALTER TABLE [dbo].[servers] ALTER COLUMN ' + QUOTENAME(column_name)
       + N' ' + type_name + N'(254) COLLATE ' + QUOTENAME(collation_name)
       + CASE WHEN is_nullable = 1 THEN N' NULL;' ELSE N' NOT NULL;' END
FROM @Columns
WHERE max_length <> -1
  AND CASE WHEN type_name = N'nvarchar' THEN max_length / 2 ELSE max_length END < 254;

SELECT DB_NAME() AS selected_database, @ApplyChanges AS apply_changes;
SELECT column_name, type_name, max_length, collation_name, is_nullable FROM @Columns;
SELECT column_name, sql_text FROM @Statements ORDER BY column_name;

IF @ApplyChanges = 0
BEGIN
    PRINT 'Preview only: no schema changes applied.';
    RETURN;
END;

DECLARE @Sql nvarchar(max);
SELECT @Sql = STRING_AGG(CAST(sql_text AS nvarchar(max)), NCHAR(10)) FROM @Statements;
IF @Sql IS NULL
BEGIN
    PRINT 'Audit columns already support at least 254 characters; no change needed.';
    RETURN;
END;

BEGIN TRY
    BEGIN TRANSACTION;
    EXEC sys.sp_executesql @Sql;
    COMMIT TRANSACTION;
    PRINT 'Server audit columns expanded successfully.';
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
