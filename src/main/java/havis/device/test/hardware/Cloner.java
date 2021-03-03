package havis.device.test.hardware;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;

public class Cloner {
	@SuppressWarnings("unchecked")
	public <T> T deepClone(T obj) {
		try {
			Class<?> clazz = obj.getClass();
			if (Collection.class.isAssignableFrom(clazz)) {
				@SuppressWarnings("rawtypes")
				Collection src = (Collection) obj;
				@SuppressWarnings("rawtypes")
				Collection dest = (Collection) clazz.newInstance();
				for (Object item : src) {
					Object itemClone = deepClone(item);
					dest.add(itemClone);
				}
				T clone = (T) dest;
				return (T) clone;
			} else if (clazz.equals(String.class) || (clazz.getSuperclass() != null && clazz.getSuperclass().equals(Number.class))
					|| clazz.equals(Boolean.class)) {
				// no cloning required, object are immutable
				return (T) obj;
			}

			T clone = (T) clazz.newInstance();
			for (Field field : clazz.getDeclaredFields()) {
				field.setAccessible(true);
				if (field.get(obj) == null || Modifier.isFinal(field.getModifiers())) {
					continue;
				}
				if (field.getType().isPrimitive() || field.getType().equals(String.class)
						|| (field.getType().getSuperclass() != null && field.getType().getSuperclass().equals(Number.class))
						|| field.getType().equals(Boolean.class) || field.getType().isEnum()) {
					field.set(clone, field.get(obj));
				} else {
					Object childObj = field.get(obj);
					if (childObj == obj) {
						field.set(clone, clone);
					} else {
						field.set(clone, deepClone(field.get(obj)));
					}
				}
			}
			return clone;
		} catch (Exception e) {
			return null;
		}
	}
}
