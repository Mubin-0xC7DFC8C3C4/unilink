package com.unilink.common;
import java.util.List;
public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {
  public static <T> PageResponse<T> of(List<T> items, String nextCursor){
    return new PageResponse<>(items, nextCursor, nextCursor != null);
  }
}
