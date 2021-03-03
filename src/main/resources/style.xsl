<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:jaxb="http://java.sun.com/xml/ns/jaxb">

	<xsl:param name="filename" />

	<xsl:template match="//jaxb:bindings/@scd[../@scd='x-schema::tns']">
		<xsl:attribute name="schemaLocation">
			<xsl:value-of select="$filename" />
  		</xsl:attribute>
	</xsl:template>

	<xsl:template match="//jaxb:schemaBindings[@map='false']" />

	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>

	<xsl:template match="/">
		<xsl:apply-templates />
	</xsl:template>

</xsl:stylesheet>