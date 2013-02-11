package krasa.xjc.plugin;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JFieldVar;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CAttributePropertyInfo;
import com.sun.tools.xjc.model.CElementPropertyInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.impl.AttributeUseImpl;
import com.sun.xml.xsom.impl.ElementDecl;
import com.sun.xml.xsom.impl.ParticleImpl;
import org.xml.sax.ErrorHandler;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

/**
 * big thanks to original author: cocorossello
 */
public class JaxbValidationsPlugins extends Plugin {

	public static final String PLUGIN_OPTION_NAME = "XJsr303Annotations";
	public static final String TARGET_NAMESPACE_PARAMETER_NAME = PLUGIN_OPTION_NAME + ":targetNamespace";

	private String namespace = "http://jaxb.dev.java.net/plugin/code-injector";
	public String targetNamespace = "TARGET_NAMESPACE";

	public String getOptionName() {
		return PLUGIN_OPTION_NAME;
	}

	@Override
	public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
		String arg1 = args[i];
		int i2 = arg1.indexOf(TARGET_NAMESPACE_PARAMETER_NAME);
		int consumed = 0;
		if (i2 > 0) {
			targetNamespace = arg1.substring(i2 + TARGET_NAMESPACE_PARAMETER_NAME.length() + "=".length());
			consumed++;
		}
		return consumed;
	}

	public List<String> getCustomizationURIs() {
		return Collections.singletonList(namespace);
	}

	public boolean isCustomizationTagName(String nsUri, String localName) {
		return nsUri.equals(namespace) && localName.equals("code");
	}

	@Override
	public void onActivated(Options opts) throws BadCommandLineException {
		super.onActivated(opts);
	}

	public String getUsage() {
		return "  -XJsr303Annotations      :  inject Bean validation annotations (JSR 303); -XJsr303Annotations:targetNamespace=http://www.foo.com/bar  :      additional settings for @Valid annotation";
	}

	public boolean run(Outline model, Options opt, ErrorHandler errorHandler) {
		try {
			for (ClassOutline co : model.getClasses()) {

				for (CPropertyInfo property : co.target.getProperties()) {
					if (property instanceof CElementPropertyInfo) {
						processElement((CElementPropertyInfo) property, co, model);
					} else if (property instanceof CAttributePropertyInfo) {
						processAttribute((CAttributePropertyInfo) property, co, model);
					}
				}
			}

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}


	static int i = 0;

	/**
	 * XS:Element
	 *
	 * @param property
	 * @param clase
	 * @param model
	 */
	public void processElement(CElementPropertyInfo property, ClassOutline clase, Outline model) {
		FieldOutline field = model.getField(property);
		XSComponent schemaComponent = property.getSchemaComponent();
		ParticleImpl particle = (ParticleImpl) schemaComponent;
		int maxOccurs = toInt(getField("maxOccurs", particle));
		int minOccurs = toInt(getField("minOccurs", particle));
		JFieldVar var = clase.implClass.fields().get(getField("privateName", property));
		if (minOccurs < 0 || minOccurs >= 1) {
			if (!hasAnnotation(var, NotNull.class)) {
				System.out.println("@NotNull: " + property.getName() + " added to class " + clase.implClass.name());
				var.annotate(NotNull.class);
			}
		}
		if (maxOccurs > 1) {
			if (!hasAnnotation(var, Size.class)) {
				System.out.println("@Size (" + minOccurs + "," + maxOccurs + ") " + property.getName() + " added to class " + clase.implClass.name());
				var.annotate(Size.class).param("min", minOccurs).param("max", maxOccurs);
			}
		}

		ElementDecl declaracion = (ElementDecl) getField("term", particle);
		if (declaracion.getType().getTargetNamespace().startsWith(targetNamespace) && declaracion.getType().isComplexType()) {
			if (!hasAnnotation(var, Valid.class)) {
				System.out.println("@Valid: " + property.getName() + " added to class " + clase.implClass.name());
				var.annotate(Valid.class);
			}
		}
		if (declaracion.getType() instanceof XSSimpleType) {
			processType((XSSimpleType) declaracion.getType(), var, property.getName(), clase.implClass.name());
		} else if (declaracion.getType().getBaseType() instanceof XSSimpleType) {
			processType((XSSimpleType) declaracion.getType().getBaseType(), var, property.getName(), clase.implClass.name());
		}

		// if(declaracion.getType() instanceof
		// if(declaracion.getType().ge)
		// procesaType(declaracion.getType().getBaseType(),var);
	}

	private int toInt(Object maxOccurs) {
		if (maxOccurs instanceof BigInteger) {
			//xjc
			return ((BigInteger) maxOccurs).intValue();
		} else if (maxOccurs instanceof Integer) {
			//cxf-codegen
			return (Integer) maxOccurs;
		} else {
			throw new IllegalArgumentException("unknown type " + maxOccurs.getClass());
		}
	}

	/**
	 * XS:Attribute
	 *
	 * @param property
	 * @param clase
	 * @param model
	 */
	public void processAttribute(CAttributePropertyInfo property, ClassOutline clase, Outline model) {
		FieldOutline field = model.getField(property);
		System.out.println("Attribute " + property.getName() + " added to class " + clase.implClass.name());
		XSComponent definicion = property.getSchemaComponent();
		AttributeUseImpl particle = (AttributeUseImpl) definicion;
		JFieldVar var = clase.implClass.fields().get(getField("privateName", property));
		if (particle.isRequired()) {
			if (!hasAnnotation(var, NotNull.class)) {
				System.out.println("@NotNull: " + property.getName() + " added to class " + clase.implClass.name());
				var.annotate(NotNull.class);
			}
		}
		if (particle.getDecl().getType().getTargetNamespace().startsWith(targetNamespace) && particle.getDecl().getType().isComplexType()) {
			if (!hasAnnotation(var, Valid.class)) {
				System.out.println("@Valid: " + property.getName() + " added to class " + clase.implClass.name());
				var.annotate(Valid.class);
			}
		}
		processType(particle.getDecl().getType(), var, property.getName(), clase.implClass.name());
	}

	public void processType(XSSimpleType tipo, JFieldVar field, String campo, String clase) {
		if (tipo.getFacet("maxLength") != null || tipo.getFacet("minLength") != null) {
			Integer maxLength = tipo.getFacet("maxLength") == null ? null : parseInt(tipo.getFacet("maxLength").getValue().value);
			Integer minLength = tipo.getFacet("minLength") == null ? null : parseInt(tipo.getFacet("minLength").getValue().value);
			if (!hasAnnotation(field, Size.class)) {
				System.out.println("@Size(" + minLength + "," + maxLength + "): " + campo + " added to class " + clase);
				field.annotate(Size.class).param("min", minLength).param("max", maxLength);
			}
		}
		  /*
		  * <bindings multiple="true" node=
          * "//xs:complexType/.//xs:element[contains(@type,'IntPercentRestriction')]"
          * > <annox:annotate> <annox:annotate
          * annox:class="javax.validation.constraints.Digits" integer="3"
          * fraction="2" /> <annox:annotate
          * annox:class="javax.validation.constraints.Min" value="-100" />
          * value="100" /> </annox:annotate> </bindings>
          *//*
			   * <xs:restriction base="xs:decimal"> <xs:fractionDigits value="2"/>
               * <xs:maxInclusive value="100.00"/> <xs:minInclusive
               * value="-100.00"/> <xs:totalDigits value="5"/> </xs:restriction>
               */

		XSFacet maxInclusive = tipo.getFacet("maxInclusive");
		if (maxInclusive != null && isValidValue(maxInclusive) && !hasAnnotation(field, DecimalMax.class)) {
			System.out.println("@DecimalMax(" + maxInclusive.getValue().value + "): " + campo + " added to class " + clase);
			field.annotate(DecimalMax.class).param("value", maxInclusive.getValue().value);
		}
		XSFacet minInclusive = tipo.getFacet("minInclusive");
		if (minInclusive != null && isValidValue(minInclusive) && !hasAnnotation(field, DecimalMin.class)) {
			System.out.println("@DecimalMin(" + minInclusive.getValue().value + "): " + campo + " added to class " + clase);
			field.annotate(DecimalMin.class).param("value", minInclusive.getValue().value);
		}
		if (tipo.getFacet("totalDigits") != null) {
			Integer totalDigits = tipo.getFacet("totalDigits") == null ? null : parseInt(tipo.getFacet("totalDigits").getValue().value);
			int fractionDigits = tipo.getFacet("fractionDigits") == null ? 0 : parseInt(tipo.getFacet("fractionDigits").getValue().value);
			if (!hasAnnotation(field, Digits.class)) {
				System.out.println("@Digits(" + totalDigits + "," + fractionDigits + "): " + campo + " added to class " + clase);
				JAnnotationUse annox = field.annotate(Digits.class).param("integer", (totalDigits - fractionDigits));
				if (tipo.getFacet("fractionDigits") != null) {
					annox.param("fraction", fractionDigits);
				}
			}
		}
		/**
		 *      <annox:annotate annox:class="javax.validation.constraints.Pattern"
		 message="Name can only contain capital letters, numbers and the simbols '-', '_', '/', ' '"
		 regexp="^[A-Z0-9_\s//-]*" />
		 */
		if (tipo.getFacet("pattern") != null) {
			String pattern = tipo.getFacet("pattern").getValue().value;
			//cxf-codegen fix
			if (!"\\c+".equals(pattern)) {
				System.out.println("@Pattern(" + pattern + "): " + campo + " added to class " + clase);
				if (!hasAnnotation(field, Pattern.class)) {
					field.annotate(Pattern.class).param("regexp", pattern);
				}
			}
		}

	}

	private boolean isValidValue(XSFacet facet) {
		String value = facet.getValue().value;
		//cxf-codegen puts max and min as value when there is not anything defined in wsdl.
		return value != null && !isMax(value) && !isMin(value);
	}

	private boolean isMin(String value) {
		return equals(value, -9223372036854775808L) || equals(value, -2147483648L);
	}

	private boolean isMax(String value) {
		return equals(value, 9223372036854775807L) || equals(value, 2147483647L);
	}

	private boolean equals(String value, long val) {
		return value.equals(BigInteger.valueOf(val).toString());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public boolean hasAnnotation(JFieldVar var, Class anotacion) {
		List<JAnnotationUse> lista = (List<JAnnotationUse>) getField("annotations", var);
		if (lista != null) {
			for (JAnnotationUse uso : lista) {
				if (((Class) getField("clazz._class", uso)).getCanonicalName().equals(anotacion.getCanonicalName())) {
					return true;
				}
			}
		}
		return false;
	}


	private Integer parseInt(String valor) {
		try {

			Integer i = Integer.parseInt(valor);
			if (i < 2147483647 && i > -2147483648) {
				return i;
			}
		} catch (Exception e) {
			try {
				return (int) Math.round(Double.parseDouble(valor));

			} catch (Exception ex) {
				;
			}

		}
		return null;

	}

	/*
	 private Long parseLong(String valor) {
		  try {
			   Long i = Long.parseLong(valor);
			   if (i < 2147483647 && i > -2147483648) {
					return i;
			   }
		  } catch (Exception e) {
			   return Math.round(Double.parseDouble(valor));
		  }
		  return null;

	 }    
	 */
	private Object getField(String path, Object oo) {
		try {
			if (path.contains(".")) {
				String field = path.substring(0, path.indexOf("."));
				Field campo = oo.getClass().getDeclaredField(field);
				campo.setAccessible(true);
				Object result = campo.get(oo);
				return getField(path.substring(path.indexOf(".") + 1), result);
			} else {
				Field campo = getSimpleField(path, oo.getClass());
				campo.setAccessible(true);
				return campo.get(oo);
			}
		} catch (Exception e) {
			System.out.println("Field " + path + " not found on " + oo.getClass().getName());
		}
		return null;
	}

	private static Field getSimpleField(String fieldName, Class<?> clazz) {
		Class<?> tmpClass = clazz;
		try {
			do {
				for (Field field : tmpClass.getDeclaredFields()) {
					String candidateName = field.getName();
					if (!candidateName.equals(fieldName)) {
						continue;
					}
					field.setAccessible(true);
					return field;
				}
				tmpClass = tmpClass.getSuperclass();
			} while (clazz != null);
		} catch (Exception e) {
			System.out.println("Field '" + fieldName + "' not found on class " + clazz);
		}
		return null;
	}
}