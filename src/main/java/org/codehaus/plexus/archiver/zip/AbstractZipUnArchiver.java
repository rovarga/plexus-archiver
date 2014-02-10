package org.codehaus.plexus.archiver.zip;

/**
 *
 * Copyright 2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;
import org.codehaus.plexus.archiver.AbstractUnArchiver;
import org.codehaus.plexus.archiver.ArchiveFilterException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.util.ArchiveEntryUtils;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

/**
 * @author <a href="mailto:evenisse@codehaus.org">Emmanuel Venisse</a>
 * @version $Id$
 */
public abstract class AbstractZipUnArchiver
    extends AbstractUnArchiver
{
    private static final String NATIVE_ENCODING = "native-encoding";

    private String encoding = "UTF8";

    public AbstractZipUnArchiver()
    {
    }

    public AbstractZipUnArchiver( final File sourceFile )
    {
        super( sourceFile );
    }

    /**
     * Sets the encoding to assume for file names and comments.
     * <p/>
     * <p>
     * Set to <code>native-encoding</code> if you want your platform's native encoding, defaults to UTF8.
     * </p>
     */
    public void setEncoding( String encoding )
    {
        if ( NATIVE_ENCODING.equals( encoding ) )
        {
            encoding = null;
        }
        this.encoding = encoding;
    }

    private static class ZipEntryFileInfo
        implements PlexusIoResource
    {
        private final ZipFile zipFile;

        private final ZipEntry zipEntry;

        ZipEntryFileInfo( final ZipFile zipFile, final ZipEntry zipEntry )
        {
            this.zipFile = zipFile;
            this.zipEntry = zipEntry;
        }

        public String getName()
        {
            return zipEntry.getName();
        }

        public boolean isDirectory()
        {
            return zipEntry.isDirectory();
        }

        public boolean isFile()
        {
            return !zipEntry.isDirectory();
        }

        public InputStream getContents()
            throws IOException
        {
            return zipFile.getInputStream( zipEntry );
        }

        public long getLastModified()
        {
            final long l = zipEntry.getTime();
            return l == 0 ? PlexusIoResource.UNKNOWN_MODIFICATION_DATE : l;
        }

        public long getSize()
        {
            final long l = zipEntry.getSize();
            return l == -1 ? PlexusIoResource.UNKNOWN_RESOURCE_SIZE : l;
        }

        public URL getURL()
            throws IOException
        {
            return null;
        }

        public boolean isExisting()
        {
            return true;
        }
    }

    protected void execute()
        throws ArchiverException
    {
        getLogger().debug( "Expanding: " + getSourceFile() + " into " + getDestDirectory() );
        ZipFile zf = null;
        try
        {
            zf = new ZipFile( getSourceFile(), encoding );
            final Enumeration e = zf.getEntries();
            while ( e.hasMoreElements() )
            {
                final ZipEntry ze = (ZipEntry) e.nextElement();
                final ZipEntryFileInfo fileInfo = new ZipEntryFileInfo( zf, ze );
                if ( !isSelected( ze.getName(), fileInfo ) )
                {
                    continue;
                }
                InputStream in = zf.getInputStream( ze );
                extractFileIfIncluded( getSourceFile(), getDestDirectory(), in, ze.getName(),
                                       new Date( ze.getTime() ), ze.isDirectory(), ze.getUnixMode()!= 0 ? ze.getUnixMode() : null );
                in.close();
            }

            getLogger().debug( "expand complete" );
        }
        catch ( final IOException ioe )
        {
            throw new ArchiverException( "Error while expanding " + getSourceFile().getAbsolutePath(), ioe );
        }
        finally
        {
            if ( zf != null )
            {
                try
                {
                    zf.close();
                }
                catch ( final IOException e )
                {
                    // ignore
                }
            }
        }
    }

    private void extractFileIfIncluded( final File sourceFile, final File destDirectory, final InputStream inputStream,
                                        final String name, final Date time, final boolean isDirectory,
                                        final Integer mode )
        throws IOException, ArchiverException
    {
        try
        {
            if ( include( inputStream, name ) )
            {
                extractFile( sourceFile, destDirectory, inputStream, name, time, isDirectory, mode );
            }
        }
        catch ( final ArchiveFilterException e )
        {
            throw new ArchiverException( "Error verifying \'" + name + "\' for inclusion: " + e.getMessage(), e );
        }
    }

    protected void execute( final String path, final File outputDirectory )
        throws ArchiverException
    {
        ZipFile zipFile = null;

        try
        {
            zipFile = new ZipFile( getSourceFile(), encoding );

            final Enumeration e = zipFile.getEntries();

            while ( e.hasMoreElements() )
            {
                final ZipEntry ze = (ZipEntry) e.nextElement();
                final ZipEntryFileInfo fileInfo = new ZipEntryFileInfo( zipFile, ze );
                if ( !isSelected( ze.getName(), fileInfo ) )
                {
                    continue;
                }

                if ( ze.getName().startsWith( path ) )
                {
                    final InputStream inputStream = zipFile.getInputStream( ze );
                    extractFileIfIncluded( getSourceFile(), outputDirectory, inputStream,
                                           ze.getName(), new Date( ze.getTime() ), ze.isDirectory(),
                                           ze.getUnixMode() != 0 ? ze.getUnixMode() : null );
                    inputStream.close();
                }
            }
        }
        catch ( final IOException ioe )
        {
            throw new ArchiverException( "Error while expanding " + getSourceFile().getAbsolutePath(), ioe );
        }
        finally
        {
            if ( zipFile != null )
            {
                try
                {
                    zipFile.close();
                }
                catch ( final IOException e )
                {
                    // ignore
                }
            }
        }
    }
}
