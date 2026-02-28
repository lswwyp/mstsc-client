package com.mstsc.client.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RdpDeviceDao_Impl implements RdpDeviceDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RdpDevice> __insertionAdapterOfRdpDevice;

  private final EntityDeletionOrUpdateAdapter<RdpDevice> __deletionAdapterOfRdpDevice;

  private final EntityDeletionOrUpdateAdapter<RdpDevice> __updateAdapterOfRdpDevice;

  public RdpDeviceDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRdpDevice = new EntityInsertionAdapter<RdpDevice>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `rdp_devices` (`id`,`deviceId`,`username`,`password`,`displayName`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RdpDevice entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getDeviceId());
        statement.bindString(3, entity.getUsername());
        statement.bindString(4, entity.getPassword());
        if (entity.getDisplayName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDisplayName());
        }
        statement.bindLong(6, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfRdpDevice = new EntityDeletionOrUpdateAdapter<RdpDevice>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `rdp_devices` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RdpDevice entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfRdpDevice = new EntityDeletionOrUpdateAdapter<RdpDevice>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `rdp_devices` SET `id` = ?,`deviceId` = ?,`username` = ?,`password` = ?,`displayName` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final RdpDevice entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getDeviceId());
        statement.bindString(3, entity.getUsername());
        statement.bindString(4, entity.getPassword());
        if (entity.getDisplayName() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getDisplayName());
        }
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final RdpDevice device, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfRdpDevice.insertAndReturnId(device);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final RdpDevice device, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfRdpDevice.handle(device);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final RdpDevice device, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfRdpDevice.handle(device);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<RdpDevice>> getAllFlow() {
    final String _sql = "SELECT * FROM rdp_devices ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"rdp_devices"}, new Callable<List<RdpDevice>>() {
      @Override
      @NonNull
      public List<RdpDevice> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfPassword = CursorUtil.getColumnIndexOrThrow(_cursor, "password");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<RdpDevice> _result = new ArrayList<RdpDevice>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RdpDevice _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final String _tmpPassword;
            _tmpPassword = _cursor.getString(_cursorIndexOfPassword);
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new RdpDevice(_tmpId,_tmpDeviceId,_tmpUsername,_tmpPassword,_tmpDisplayName,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAll(final Continuation<? super List<RdpDevice>> $completion) {
    final String _sql = "SELECT * FROM rdp_devices ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<RdpDevice>>() {
      @Override
      @NonNull
      public List<RdpDevice> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfPassword = CursorUtil.getColumnIndexOrThrow(_cursor, "password");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<RdpDevice> _result = new ArrayList<RdpDevice>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final RdpDevice _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final String _tmpPassword;
            _tmpPassword = _cursor.getString(_cursorIndexOfPassword);
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new RdpDevice(_tmpId,_tmpDeviceId,_tmpUsername,_tmpPassword,_tmpDisplayName,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final long id, final Continuation<? super RdpDevice> $completion) {
    final String _sql = "SELECT * FROM rdp_devices WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<RdpDevice>() {
      @Override
      @Nullable
      public RdpDevice call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDeviceId = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceId");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfPassword = CursorUtil.getColumnIndexOrThrow(_cursor, "password");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final RdpDevice _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpDeviceId;
            _tmpDeviceId = _cursor.getString(_cursorIndexOfDeviceId);
            final String _tmpUsername;
            _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            final String _tmpPassword;
            _tmpPassword = _cursor.getString(_cursorIndexOfPassword);
            final String _tmpDisplayName;
            if (_cursor.isNull(_cursorIndexOfDisplayName)) {
              _tmpDisplayName = null;
            } else {
              _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new RdpDevice(_tmpId,_tmpDeviceId,_tmpUsername,_tmpPassword,_tmpDisplayName,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
